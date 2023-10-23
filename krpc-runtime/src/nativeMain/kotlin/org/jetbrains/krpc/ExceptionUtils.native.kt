package org.jetbrains.krpc

internal actual class DeserializedException actual constructor(
    private val toStringMessage: String,
    override val message: String,
    stacktrace: List<StackElement>,
    cause: SerializedException?,
    className: String
) : Throwable() {

    override val cause: Throwable? = cause?.deserialize()

    override fun toString(): String = toStringMessage
}

internal actual fun Throwable.stackElements(): List<StackElement> = emptyList()

actual fun SerializedException.deserialize(): Throwable {
    return DeserializedException(toStringMessage, message, stacktrace, cause, className)
}

internal actual val Throwable.qualifiedClassName: String?
    get() = this::class.qualifiedName