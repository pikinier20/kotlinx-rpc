package org.jetbrains.krpc

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.jetbrains.krpc.internal.InternalKRPCApi
import kotlin.coroutines.CoroutineContext

class LazyRPCStreamContext(private val initializer: () -> RPCStreamContext) {
    private val deferred = CompletableDeferred<RPCStreamContext>()
    private val lazyValue by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        initializer().also { deferred.complete(it) }
    }

    suspend fun awaitInitialized() = deferred.await()

    val valueOrNull get() = if (deferred.isCompleted) lazyValue else null

    fun initialize(): RPCStreamContext = lazyValue
}

class RPCStreamContext(private val callId: String, private val config: RPCConfig) {
    companion object {
        private const val STREAM_ID_PREFIX = "stream:"
    }

    private val streamId = atomic(0L)

    private var incomingStreamsInitialized: Boolean = false
    private val incomingStreams by lazy {
        incomingStreamsInitialized = true
        ConcurrentMap<String, RPCStreamInfo>()
    }

    private var incomingChannelsInitialized: Boolean = false
    private val incomingChannels by lazy {
        incomingChannelsInitialized = true
        ConcurrentMap<String, Channel<Any?>>()
    }

    private var outgoingStreamsInitialized: Boolean = false
    val outgoingStreams: Channel<RPCStreamInfo> by lazy {
        outgoingStreamsInitialized = true
        Channel(capacity = Channel.UNLIMITED)
    }

    private var incomingHotFlowsInitialized: Boolean = false
    val incomingHotFlows: Channel<FlowCollector<Any?>> by lazy {
        incomingHotFlowsInitialized = true
        Channel(Channel.UNLIMITED)
    }

    fun <StreamT : Any> registerStream(stream: StreamT, streamKind: StreamKind, elementSerializer: KSerializer<Any?>): String {
        val id = "$STREAM_ID_PREFIX${streamId.getAndIncrement()}"
        outgoingStreams.trySend(RPCStreamInfo(callId, id, stream, streamKind, elementSerializer))
        return id
    }

    fun <StreamT : Any> prepareStream(
        streamId: String,
        streamKind: StreamKind,
        stateFlowInitialValue: Any?,
        elementSerializer: KSerializer<Any?>,
    ): StreamT {
        val incoming: Channel<Any?> = Channel(Channel.UNLIMITED)
        incomingChannels[streamId] = incoming

        val stream = streamOf<StreamT>(streamKind, stateFlowInitialValue, incoming)
        incomingStreams[streamId] = RPCStreamInfo(callId, streamId, stream, streamKind, elementSerializer)
        return stream
    }

    @OptIn(InternalKRPCApi::class)
    @Suppress("UNCHECKED_CAST")
    private fun <StreamT : Any> streamOf(streamKind: StreamKind, stateFlowInitialValue: Any?, incoming: Channel<Any?>): StreamT {
        suspend fun consumeFlow(collector: FlowCollector<Any?>, onError: (Throwable) -> Unit) {
            for (message in incoming) {
                when (message) {
                    is StreamCancel -> onError(message.cause.cause.deserialize())
                    is StreamEnd -> return
                    else -> collector.emit(message)
                }
            }
        }

        return when (streamKind) {
            StreamKind.Flow -> flow {
                consumeFlow(this) { e -> throw e }
            }

            StreamKind.SharedFlow -> {
                val sharedFlow: MutableSharedFlow<Any?> = config.sharedFlowBuilder()

                object : RPCIncomingHotFlow(sharedFlow, ::consumeFlow), MutableSharedFlow<Any?> by sharedFlow {
                    override suspend fun collect(collector: FlowCollector<Any?>): Nothing {
                        super.collect(collector)
                    }

                    override suspend fun emit(value: Any?) {
                        super.emit(value)
                    }
                }.also { incomingHotFlows.trySend(it) }
            }

            StreamKind.StateFlow -> {
                val stateFlow = MutableStateFlow(stateFlowInitialValue)

                object : RPCIncomingHotFlow(stateFlow, ::consumeFlow), MutableStateFlow<Any?> by stateFlow {
                    override suspend fun collect(collector: FlowCollector<Any?>): Nothing {
                        super.collect(collector)
                    }

                    override suspend fun emit(value: Any?) {
                        super.emit(value)
                    }
                }.also { incomingHotFlows.trySend(it) }
            }
        } as StreamT
    }

    suspend fun closeStream(message: RPCMessage.StreamFinished) {
        incomingChannelOf(message.streamId).send(StreamEnd)
    }

    suspend fun cancelStream(message: RPCMessage.StreamCancel) {
        incomingChannelOf(message.streamId).send(StreamCancel(message))
    }

    suspend fun send(message: RPCMessage.StreamMessage, json: Json) {
        val info = incomingStreams[message.streamId] ?: error("Unknown stream ${message.streamId}")
        val result = json.decodeFromString(info.elementSerializer, message.data)
        incomingChannelOf(message.streamId).send(result)
    }

    private fun incomingChannelOf(stremaId: String): Channel<Any?> {
        return incomingChannels[stremaId] ?: error("Expected stream with id \"$stremaId\" to be present in incomingChannels")
    }

    fun close() {
        if (incomingChannelsInitialized) {
            for (channel in incomingChannels.values) {
                channel.close()
            }

            incomingChannels.clear()
        }

        if (incomingStreamsInitialized) {
            incomingStreams.clear()
        }

        if (outgoingStreamsInitialized) {
            outgoingStreams.close()
        }

        if (incomingHotFlowsInitialized) {
            incomingHotFlows.close()
        }
    }
}

private object StreamEnd

private class StreamCancel(
    val cause: RPCMessage.StreamCancel
)

private abstract class RPCIncomingHotFlow(
    private val rawFlow: MutableSharedFlow<Any?>,
    private val consume: suspend (FlowCollector<Any?>, onError: (Throwable) -> Unit) -> Unit,
) : MutableSharedFlow<Any?> {
    private val subscriptionContexts by lazy { mutableListOf<CoroutineContext>() }

    override suspend fun collect(collector: FlowCollector<Any?>): Nothing {
        val context = currentCoroutineContext()

        if (context.isActive) {
            subscriptionContexts.add(context)

            context.job.invokeOnCompletion {
                subscriptionContexts.remove(context)
            }
        }

        rawFlow.collect(collector)
    }

    // value can be ignored, as actual values are coming from the
    override suspend fun emit(value: Any?) {
        consume(rawFlow) { e ->
            subscriptionContexts.forEach { it.cancel(CancellationException(e.message, e)) }
        }
    }
}