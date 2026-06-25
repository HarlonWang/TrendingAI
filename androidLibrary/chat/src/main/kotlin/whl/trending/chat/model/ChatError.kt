package whl.trending.chat.model

/**
 * 聊天失败的分类。[retryable] 决定 UI 是否给"重试"按钮：
 * 配额用完 / 请求非法（如历史超限）重试也没用，故不可重试。
 */
enum class ChatErrorCategory(val retryable: Boolean) {
    NETWORK(true),       // 断网 / DNS / 连接失败
    TIMEOUT(true),       // 读/请求超时
    AUTH(false),         // 401/403，API Key 无效/无权限（BYOK 直连最高频），重试无用
    QUOTA(false),        // 429，今日额度用完
    SERVER(true),        // 5xx，服务端/上游错误，可重试
    BAD_REQUEST(false),  // 其它 4xx，客户端请求非法，重试无用
    UNKNOWN(true),       // 兜底
}

/**
 * 一次聊天失败的结构化信息。
 *
 * @param category 分类，驱动是否可重试与兜底文案
 * @param code 服务端机器可读错误码（如 `content_too_long`/`quota_global`），优先据此选具体文案
 * @param httpStatus 有 HTTP 响应时的状态码
 * @param detail 服务端 error 文案或异常摘要，仅用于日志/调试，不直接展示给用户
 */
data class ChatError(
    val category: ChatErrorCategory,
    val code: String? = null,
    val httpStatus: Int? = null,
    val detail: String? = null,
)
