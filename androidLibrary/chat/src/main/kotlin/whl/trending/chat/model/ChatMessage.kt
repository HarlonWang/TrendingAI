package whl.trending.chat.model

/** 消息角色 */
enum class Role { USER, ASSISTANT }

/**
 * 一条聊天消息。
 *
 * @param content 文本内容；assistant 为 Markdown 源串，渲染时按需解析
 * @param images 用户随消息发送的图片（压缩后的本地缓存文件路径，仅 USER 消息使用）；
 *   UI 直接按路径渲染，发送时由 transport 层读文件转 base64 内嵌
 * @param error 非空表示这条 assistant 消息是一次失败（按 [ChatError.category] 区分展示）
 */
data class ChatMessage(
    val id: Long,
    val role: Role,
    val content: String,
    val images: List<String> = emptyList(),
    val error: ChatError? = null,
)
