package whl.trending.chat.model

/**
 * 聊天失败的分类。[retryable] 决定 UI 是否给"重试"按钮：
 * 配额用完 / 请求非法（如历史超限）重试也没用，故不可重试。
 */
enum class ChatErrorCategory(val retryable: Boolean) {
    NETWORK(true),       // 断网 / DNS / 连接失败
    TIMEOUT(true),       // 读/请求超时
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
 * @param tier 429 触顶时的配额档位（`anonymous`/`user`），驱动触顶卡片形态（登录 CTA vs waitlist CTA）
 * @param authDegraded 发请求时 app 自认已登录、服务端却按匿名档处理（token 缺失或被拒后静默降级）。
 *   驱动触顶卡片给「登录态未生效」的如实提示，而非「登录后已解锁、重发即可」的误导循环。
 */
data class ChatError(
    val category: ChatErrorCategory,
    val code: String? = null,
    val httpStatus: Int? = null,
    val detail: String? = null,
    val tier: String? = null,
    val authDegraded: Boolean = false,
) {
    companion object {
        /** 服务端 429 响应 tier 字段的取值，与 Worker 端 chat.js 保持一致 */
        const val TIER_ANONYMOUS = "anonymous"
        const val TIER_USER = "user"
        const val TIER_PRO = "pro"

        /** 个人配额触顶的机器码（服务端 chat.js quotaError）；重试放行与专属卡片选择都以它为判据 */
        const val CODE_QUOTA_DEVICE = "quota_device"

        /** 详细解读登录闸的机器码（服务端 detail-summary，403）：匿名点未缓存条目 → 登录卡转化，
         *  登录成功后与 quota_device 同样享受 retry 放行例外 */
        const val CODE_LOGIN_REQUIRED = "login_required"

        /** 上游按访客所在地区拒绝（服务端 openai.js）。虽是 502、category 归 SERVER，
         *  但**重试必然同样被拒**——UI 据此换文案并隐藏重试按钮，不让用户空转。 */
        const val CODE_REGION_BLOCKED = "upstream_region_blocked"
    }
}
