package whl.trending.chat.engine

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import whl.trending.chat.model.ChatErrorCategory

/** SocketTimeout 先于 IOException 判断（前者是后者子类）。 */
internal actual fun classifyTransportException(t: Throwable): ChatErrorCategory? = when (t) {
    is SocketTimeoutException -> ChatErrorCategory.TIMEOUT
    is UnknownHostException, is ConnectException -> ChatErrorCategory.NETWORK
    is IOException -> ChatErrorCategory.NETWORK
    else -> null
}
