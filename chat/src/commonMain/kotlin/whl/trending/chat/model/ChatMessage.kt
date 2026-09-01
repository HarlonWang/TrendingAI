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
 * @param sources 联网搜索的引用来源（随消息持久化，尾部 SourcesRow 渲染）
 * @param searching 流式过程中「正在搜索」瞬态指示（不持久化）
 * @param model 生成本条消息的模型 id（气泡上标注用），可空
 */
data class ChatMessage(
    val id: Long,
    val role: Role,
    val content: String,
    val images: List<String> = emptyList(),
    val error: ChatError? = null,
    val sources: List<SourceRef> = emptyList(),
    val searching: Boolean = false,
    val model: String? = null,
)
