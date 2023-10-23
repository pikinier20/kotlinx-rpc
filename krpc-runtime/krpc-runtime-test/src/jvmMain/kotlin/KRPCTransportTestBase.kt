@file:Suppress("FunctionName")

package org.jetbrains.krpc.test

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.jetbrains.krpc.RPC
import org.jetbrains.krpc.RPCTransport
import org.jetbrains.krpc.client.clientOf
import org.jetbrains.krpc.server.RPCServerEngine
import org.jetbrains.krpc.server.serverOf
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.*

abstract class KRPCTransportTestBase {
    abstract val clientTransport: RPCTransport
    abstract val serverTransport: RPCTransport

    private lateinit var backend: RPCServerEngine<KRPCTestService>
    private lateinit var client: KRPCTestService

    @BeforeTest
    fun start() {
        backend = RPC.serverOf<KRPCTestService>(KRPCTestServiceBackend(), serverTransport)
        client = RPC.clientOf<KRPCTestService>(clientTransport)
    }

    @Test
    fun empty() {
        backend.cancel()
        client.cancel()
    }

    @Test
    fun returnType() {
        runBlocking {
            assertEquals("test", client.returnType())
        }
    }

    @Test
    fun simpleWithParams() {
        runBlocking {
            assertEquals("name".reversed(), client.simpleWithParams("name"))
        }
    }

    @Test
    fun genericReturnType() {
        runBlocking {
            assertEquals(listOf("hello", "world"), client.genericReturnType())
        }
    }

    @Test
    fun doubleGenericReturnType() {
        val result = runBlocking { client.doubleGenericReturnType() }
        assertEquals(listOf(listOf("1", "2"), listOf("a", "b")), result)
    }

    @Test
    fun paramsSingle() {
        val result = runBlocking { client.paramsSingle("test") }
        assertEquals(Unit, result)
    }

    @Test
    fun paramsDouble() {
        val result = runBlocking { client.paramsDouble("test", "test2") }
        assertEquals(Unit, result)
    }

    @Test
    @Ignore
    fun varargParams() {
        val result = runBlocking { client.varargParams("test", "test2", "test3") }
        assertEquals(Unit, result)
    }

    @Test
    fun genericParams() {
        val result = runBlocking { client.genericParams(listOf("test", "test2", "test3")) }
        assertEquals(Unit, result)
    }

    @Test
    fun doubleGenericParams() {
        val result = runBlocking { client.doubleGenericParams(listOf(listOf("test", "test2", "test3"))) }
        assertEquals(Unit, result)
    }

    @Test
    fun mapParams() {
        val result = runBlocking { client.mapParams(mapOf("key" to mapOf(1 to listOf("test", "test2", "test3")))) }
        assertEquals(Unit, result)
    }

    @Test
    fun customType() {
        val result = runBlocking { client.customType(TestClass()) }
        assertEquals(TestClass(), result)
    }

    @Test
    fun nullable() {
        val result = runBlocking { client.nullable("test") }
        assertEquals(TestClass(), result)

        val result2 = runBlocking { client.nullable(null) }
        assertEquals(null, result2)
    }

    @Test
    fun variance() {
        val result = runBlocking { client.variance(TestList(), TestList2<TestClass>()) }
        assertEquals(TestList<TestClass>(3), result)
    }

    @Test
    fun incomingStreamSyncCollect() {
        val result = runBlocking { client.incomingStreamSyncCollect(flowOf("test1", "test2", "test3")) }
        assertEquals(3, result)
    }

    @Test
    fun incomingStreamAsyncCollect() {
        val result = runBlocking { client.incomingStreamSyncCollect(flowOf("test1", "test2", "test3")) }
        assertEquals(3, result)
    }

    @Test
    fun outgoingStream() {
        runBlocking {
            val result = client.outgoingStream()
            assertEquals(listOf("a", "b", "c"), result.toList(mutableListOf()))
        }
    }

    @Test
    fun bidirectionalStream() {
        runBlocking {
            val result = client.bidirectionalStream(flowOf("test1", "test2", "test3"))
            assertEquals(
                listOf("test1".reversed(), "test2".reversed(), "test3".reversed()),
                result.toList(mutableListOf()),
            )
        }
    }

    @Test
    fun streamInDataClass() {
        runBlocking {
            val result = client.streamInDataClass(payload())
            assertEquals(8, result)
        }
    }

    @Test
    fun streamInStream() {
        runBlocking {
            val result = client.streamInStream(payloadStream())
            assertEquals(30, result)
        }
    }

    @Test
    fun streamOutDataClass() {
        runBlocking {
            val result = client.streamOutDataClass()
            assertEquals("test0", result.payload)
            assertEquals(listOf("a0", "b0", "c0"), result.stream.toList(mutableListOf()))
        }
    }

    @Test
    fun streamOfStreamsInReturn() {
        runBlocking {
            val result = client.streamOfStreamsInReturn().map {
                it.toList(mutableListOf())
            }.toList(mutableListOf())
            assertEquals(listOf(listOf("a", "b", "c"), listOf("1", "2", "3")), result)
        }
    }

    @Test
    fun streamOfPayloadsInReturn() {
        runBlocking {
            val result = client.streamOfPayloadsInReturn().map {
                it.stream.toList(mutableListOf()).joinToString()
            }.toList(mutableListOf()).joinToString()
            assertEquals(
                "a0, b0, c0, a1, b1, c1, a2, b2, c2, a3, b3, c3, a4, b4, c4, a5, b5, c5, a6, b6, c6, a7, b7, c7, a8, b8, c8, a9, b9, c9",
                result,
            )
        }
    }

    @Test
    fun streamInDataClassWithStream() {
        runBlocking {
            val result = client.streamInDataClassWithStream(payloadWithPayload())
            assertEquals(5, result)
        }
    }

    @Test
    fun streamInStreamWithStream() {
        runBlocking {
            val result = client.streamInStreamWithStream(payloadWithPayloadStream())
            assertEquals(5, result)
        }
    }

    @Test
    fun returnPayloadWithPayload() {
        runBlocking {
            client.returnPayloadWithPayload().collectAndPrint()
        }
    }

    @Test
    fun returnFlowPayloadWithPayload() {
        runBlocking {
            client.returnFlowPayloadWithPayload().collect {
                it.collectAndPrint()
            }
        }
    }

    @Test
    fun bidirectionalFlowOfPayloadWithPayload() {
        runBlocking {
            val result = client.bidirectionalFlowOfPayloadWithPayload(
                flow {
                    repeat(5) {
                        emit(payloadWithPayload(10))
                    }
                },
            )

            result.collect {
                it.collectAndPrint()
            }
        }
    }

    @Test
    fun bidirectionalAsyncStream() {
        runBlocking {
            val flow = MutableSharedFlow<Int>()
            val result = client.echoStream(flow.take(10))
            launch {
                var id = 0
                result.collect {
                    assertEquals(id, it)
                    id++
                    flow.emit(id)
                }
            }

            flow.emit(0)
        }
    }

    @Test
    fun `RPC should be able to receive 100_000 ints in reasonable time`() {
        runBlocking {
            val n = 100_000
            assertEquals(client.getNInts(n).last(), n)
        }
    }

    @Test
    fun `RPC should be able to receive 100_000 ints with batching in reasonable time`() {
        runBlocking {
            val n = 100_000
            assertEquals(client.getNIntsBatched(n).last().last(), n)
        }
    }

    @Test
    fun testByteArraySerialization() {
        runBlocking {
            client.bytes("hello".toByteArray())
            client.nullableBytes(null)
            client.nullableBytes("hello".toByteArray())
        }
    }

    @Test
    fun testException() {
        runBlocking {
            try {
                client.throwsIllegalArgument("me")
            } catch (e: IllegalArgumentException) {
                assertEquals("me", e.message)
            }
            try {
                client.throwsThrowable("me")
            } catch (e: Throwable) {
                assertEquals("me", e.message)
            }
            try {
                client.throwsUNSTOPPABLEThrowable("me")
            } catch (e: Throwable) {
                assertEquals("me", e.message)
            }
        }
    }

    @Test
    fun testNullables() {
        runBlocking {
            assertEquals(1, client.nullableInt(1))
            assertNull(client.nullable(null))
        }
    }

    @Test
    fun testNullableLists() {
        runBlocking {
            assertNull(client.nullableList(null))

            assertEquals(emptyList<Int>(), client.nullableList(emptyList()))
            assertEquals(listOf(1), client.nullableList(listOf(1)))
        }
    }

    @Test
    fun testServerCallCancellation() {
        runBlocking {
            val flag: Channel<Boolean> = Channel()
            val remote = launch {
                try {
                    client.delayForever().collect {
                        flag.send(it)
                    }
                } catch (e: CancellationException) {
                    throw e
                }

                fail("Shall not pass here")
            }.apply {
                invokeOnCompletion { cause: Throwable? ->
                    if (cause != null && cause !is CancellationException) {
                        throw cause
                    }
                }
            }

            flag.receive()
            remote.cancelAndJoin()
        }
    }

    @Test
    fun `rpc continuation is called in the correct scope and doesn't block other rpcs`() {
        runBlocking {
            val inContinuation = Semaphore(1)
            val running = AtomicBoolean(true)

            // start a coroutine that block the thread after continuation
            val c1 = async(Dispatchers.IO) { // make a rpc call
                client.answerToAnything("hello")

                // now we are in the continuation
                // important for test: don't use suspending primitives to signal it, as we want to make sure we have a blocked thread,
                // when the second coroutine is launched
                inContinuation.release()

                // let's block the thread
                while (running.get()) {
                    // do nothing
                }
            }

            // wait, till the Rpc continuation thread is blocked
            assertTrue(inContinuation.tryAcquire(100, TimeUnit.MILLISECONDS))

            val c2 = async { // make a call
                // and make sure the continuation is executed, even though another call's continuation has blocked its thread.
                client.answerToAnything("hello")
                running.set(false)
            }

            awaitAll(c1, c2)
        }
    }
}