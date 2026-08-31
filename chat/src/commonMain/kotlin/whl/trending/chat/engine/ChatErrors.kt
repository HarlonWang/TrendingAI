package whl.trending.chat.engine

import io.ktor.client.plugins.HttpRequestTimeoutException
import whl.trending.chat.model.ChatError
import whl.trending.chat.model.ChatErrorCategory

/** 聊天失败异常，携带结构化 [ChatError]；引擎边界统一抛出，UI/VM 直接读 [error]。 */
class ChatException(val error: ChatError) : Exception(error.detail)

/** 传输层异常的平台归类（超时/断网各平台异常类型不同）；认不出返回 null。 */
internal expect fun classifyTransportException(t: Throwable): ChatErrorCategory?

/** 把 HTTP 状态码 / 传输异常归类为 [ChatError]。纯函数，便于单测。 */
object ChatErrors {

    /**
     * 非 2xx 响应 → 分类。
     * @param code 服务端机器可读错误码（可空），透传给 UI 选具体文案
     * @param bodyError 服务端 error 文案（用于 detail）
     * @param tier 429 响应体的配额档位（可空），透传给 UI 选触顶卡片形态
     */
    fun forStatus(status: Int, code: String?, bodyError: String?, tier: String? = null): ChatError {
        val category = when {
            status == 429 -> ChatErrorCategory.QUOTA
            status in 500..599 -> ChatErrorCategory.SERVER
            status in 400..499 -> ChatErrorCategory.BAD_REQUEST
            else -> ChatErrorCategory.UNKNOWN
        }
        return ChatError(category, code = code, httpStatus = status, detail = bodyError, tier = tier)
    }

    /**
     * 已登录却拿到匿名档的 429 → 标记 [ChatError.authDegraded]：token 缺失（刷新失败）或被服务端
     * 拒绝后静默降级，重发大概率还是匿名档。仅在「发请求时自认已登录 && 响应 tier=anonymous」时标记；
     * 匿名触顶后才登录的正常路径（发请求时未登录）不受影响。
     */
    fun markAuthDegraded(error: ChatError, sentAsLoggedIn: Boolean): ChatError =
        if (sentAsLoggedIn && error.tier == ChatError.TIER_ANONYMOUS) {
            error.copy(authDegraded = true)
        } else {
            error
        }

    /** 传输/未知异常 → 分类。ktor 公共超时先判，平台细分交给 expect。 */
    fun forThrowable(t: Throwable): ChatError {
        val category = when {
            t is HttpRequestTimeoutException -> ChatErrorCategory.TIMEOUT
            else -> classifyTransportException(t) ?: ChatErrorCategory.UNKNOWN
        }
        return ChatError(category, detail = t.toString())
    }
}
