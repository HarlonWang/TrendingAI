package whl.trending.chat.model

/** 消息角色 */
enum class Role { USER, ASSISTANT }

/** 消息状态：发送中 / 完成 / 出错（可重试）/ 今日额度用完（不可重试） */
enum class MessageStatus { SENDING, DONE, ERROR, QUOTA_EXCEEDED }

/**
 * 一条聊天消息。
 *
 * @param content 文本内容；assistant 为 Markdown 源串，渲染时按需解析
 */
data class ChatMessage(
    val id: Long,
    val role: Role,
    val content: String,
    val status: MessageStatus = MessageStatus.DONE,
)
