package whl.trending.chat.engine

import io.ktor.client.engine.darwin.DarwinHttpRequestException
import io.ktor.network.sockets.SocketTimeoutException
import platform.Foundation.NSURLErrorCannotConnectToHost
import platform.Foundation.NSURLErrorCannotFindHost
import platform.Foundation.NSURLErrorDNSLookupFailed
import platform.Foundation.NSURLErrorNetworkConnectionLost
import platform.Foundation.NSURLErrorNotConnectedToInternet
import platform.Foundation.NSURLErrorTimedOut
import whl.trending.chat.model.ChatErrorCategory

/** darwin 引擎把 NSURLError 包进 [DarwinHttpRequestException]，按错误码归类。 */
internal actual fun classifyTransportException(t: Throwable): ChatErrorCategory? = when {
    t is SocketTimeoutException -> ChatErrorCategory.TIMEOUT
    t is DarwinHttpRequestException -> when (t.origin.code) {
        NSURLErrorTimedOut -> ChatErrorCategory.TIMEOUT
        NSURLErrorNotConnectedToInternet,
        NSURLErrorNetworkConnectionLost,
        NSURLErrorCannotFindHost,
        NSURLErrorCannotConnectToHost,
        NSURLErrorDNSLookupFailed,
        -> ChatErrorCategory.NETWORK
        else -> ChatErrorCategory.NETWORK
    }
    else -> null
}
