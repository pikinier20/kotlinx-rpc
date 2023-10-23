package org.jetbrains.krpc.client

import org.jetbrains.krpc.*
import org.jetbrains.krpc.internal.InternalKRPCApi
import org.jetbrains.krpc.internal.RPCClientProvider
import org.jetbrains.krpc.internal.findRPCProviderInCompanion
import org.jetbrains.krpc.internal.kClass
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * Creates a client for the specified RPC interface using the provided transport.
 *
 * @param transport the transport to be used for communication with the remote server
 * @return an instance of the client for the specified RPC interface
 */
inline fun <reified T : RPC> RPC.Companion.clientOf(
    transport: RPCTransport,
    noinline configBuilder: RPCConfigBuilder.Client.() -> Unit = {},
): T {
    return clientOf(typeOf<T>(), transport, configBuilder)
}

/**
 * Creates a client of the specified RPC type using the given RPCEngine.
 *
 * @param serviceType The type of the service to retrieve.
 * @param transport the transport to be used for communication with the remote server
 * @return A client of the specified RPC type.
 */
fun <T : RPC> RPC.Companion.clientOf(
    serviceType: KType,
    transport: RPCTransport,
    configBuilder: RPCConfigBuilder.Client.() -> Unit = {},
): T {
    val config = RPCConfigBuilder.Client().apply(configBuilder).build()
    val engine = RPCClientEngine(transport, serviceType, config)
    return clientOf(serviceType, engine)
}

/**
 * Returns an instance of the specified service type from the given RPC engine.
 *
 * @param serviceType The type of the service to retrieve.
 * @param engine The RPC engine used to retrieve the service.
 * @return An instance of the specified service type.
 */
@OptIn(InternalKRPCApi::class)
fun <T : RPC> RPC.Companion.clientOf(serviceType: KType, engine: RPCEngine): T {
    return clientOf(serviceType.kClass(), engine)
}

/**
 * Creates a client of the specified RPC type using the given RPCEngine.
 *
 * @param engine The RPCEngine instance used for creating the client.
 * @return A client of the specified RPC type.
 */
inline fun <reified T : RPC> RPC.Companion.clientOf(engine: RPCEngine): T {
    return clientOf(T::class, engine)
}

/**
 * Returns an instance of the specified service type from the given RPC engine.
 *
 * @param kClass The type of the service to retrieve.
 * @param engine The RPC engine used to retrieve the service.
 */
@OptIn(InternalKRPCApi::class)
fun <T : RPC> RPC.Companion.clientOf(kClass: KClass<T>, engine: RPCEngine): T {
    val withRPCClientObject = findRPCProviderInCompanion<RPCClientProvider<T>>(kClass)
    return withRPCClientObject.client(engine)
}

@Suppress("unused")
@Deprecated(
    "All RPC methods migrated to [RPC] scope",
    level = DeprecationLevel.WARNING,
    replaceWith = ReplaceWith(
        "RPC.clientOf<T>(transport)", "org.jetbrains.krpc.client.clientOf", "org.jetbrains.krpc.RPC"
    )
)
inline fun <reified T : RPC> rpcServiceOf(transport: RPCTransport): T = RPC.clientOf(transport)

@Suppress("unused")
@Deprecated(
    "All RPC methods migrated to the [RPC] scope",
    level = DeprecationLevel.WARNING,
    replaceWith = ReplaceWith("RPC.clientOf<T>(engine)", "org.jetbrains.krpc.client.clientOf", "org.jetbrains.krpc.RPC")
)
inline fun <reified T : RPC> rpcServiceOf(engine: RPCEngine): T = RPC.clientOf(engine)

@Deprecated(
    "All RPC methods migrated to the [RPC] scope",
    level = DeprecationLevel.WARNING,
    replaceWith = ReplaceWith(
        "RPC.clientOf<T>(serviceType, engine)",
        "org.jetbrains.krpc.client.clientOf", "org.jetbrains.krpc.RPC"
    )
)
fun <T : RPC> rpcServiceOf(serviceType: KType, engine: RPCEngine): T = RPC.clientOf(serviceType, engine)