package whl.trending.chat.model

/** 消息角色 */
enum class Role { USER, ASSISTANT }

/**
 * 一条聊天消息。
 *
 * @param content 文本内容；assistant 为 Markdown 源串，渲染时按需解析
 * @param error 非空表示这条 assistant 消息是一次失败（按 [ChatError.category] 区分展示）
 */
data class ChatMessage(
    val id: Long,
    val role: Role,
    val content: String,
    val error: ChatError? = null,
)
