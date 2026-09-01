package whl.trending.chat

/**
 * 进入聊天时可携带的初始上下文（一个内容条目的轻量引用）。
 * 通用 AI 助手入口传 null。
 *
 * @param source 条目所属数据源（`github` 等，与服务端 contents 表口径一致）
 * @param externalId 条目在数据源内的 ID（GitHub 为 `owner/repo`），同入口续接判定的锚点
 */
data class ChatContext(
    val title: String,
    val summary: String? = null,
    val sourceUrl: String? = null,
    val source: String? = null,
    val externalId: String? = null,
)
