package whl.trending.chat.engine

import io.ktor.client.plugins.HttpRequestTimeoutException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import whl.trending.chat.model.ChatError
import whl.trending.chat.model.ChatErrorCategory

/** 聊天失败异常，携带结构化 [ChatError]；引擎边界统一抛出，UI/VM 直接读 [error]。 */
class ChatException(val error: ChatError) : Exception(error.detail)

/** 把 HTTP 状态码 / 传输异常归类为 [ChatError]。纯函数，便于单测。 */
object ChatErrors {

    /**
     * 非 2xx 响应 → 分类。
     * @param code 服务端机器可读错误码（可空），透传给 UI 选具体文案
     * @param bodyError 服务端 error 文案（用于 detail）
     */
    fun forStatus(status: Int, code: String?, bodyError: String?): ChatError {
        val category = when {
            status == 429 -> ChatErrorCategory.QUOTA
            status in 500..599 -> ChatErrorCategory.SERVER
            status in 400..499 -> ChatErrorCategory.BAD_REQUEST
            else -> ChatErrorCategory.UNKNOWN
        }
        return ChatError(category, code = code, httpStatus = status, detail = bodyError)
    }

    /** 传输/未知异常 → 分类。SocketTimeout 先于 IOException 判断（前者是后者子类）。 */
    fun forThrowable(t: Throwable): ChatError {
        val category = when (t) {
            is SocketTimeoutException, is HttpRequestTimeoutException -> ChatErrorCategory.TIMEOUT
            is UnknownHostException, is ConnectException -> ChatErrorCategory.NETWORK
            is IOException -> ChatErrorCategory.NETWORK
            else -> ChatErrorCategory.UNKNOWN
        }
        return ChatError(category, detail = t.toString())
    }
}
