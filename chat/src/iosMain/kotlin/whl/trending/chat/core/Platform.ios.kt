package whl.trending.chat.core

internal actual fun logWarn(tag: String, message: String, error: Throwable?) {
    println("W/$tag: $message${error?.let { " $it" } ?: ""}")
}
