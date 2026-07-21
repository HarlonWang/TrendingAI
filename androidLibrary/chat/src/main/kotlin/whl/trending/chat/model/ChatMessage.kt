package whl.trending.chat.model

/** 消息角色 */
enum class Role { USER, ASSISTANT }

/**
 * 消息由哪条管线生成：驱动 chip 可见性与 retry 路由（解读失败重试回 detail 管线），渲染无差别。
 * 若未来新增 kind 需要消息级再生成参数或差异化渲染，届时再升级为 sealed interface——
 * 会话仅内存存储，重构无兼容成本。
 */
enum class MessageKind { CHAT, DETAIL_SUMMARY, DEEP_RESEARCH }

/**
 * 一条聊天消息。
 *
 * @param content 文本内容；assistant 为 Markdown 源串，渲染时按需解析
 * @param images 用户随消息发送的图片（压缩后的本地缓存文件路径，仅 USER 消息使用）；
 *   UI 直接按路径渲染，发送时由 transport 层读文件转 base64 内嵌
 * @param error 非空表示这条 assistant 消息是一次失败（按 [ChatError.category] 区分展示）
 * @param kind 生成管线标记，默认普通对话
 * @param sources 联网搜索的引用来源（随消息持久化，尾部 SourcesRow 渲染）
 * @param searching 流式过程中「正在搜索」瞬态指示（不持久化）
 */
data class ChatMessage(
    val id: Long,
    val role: Role,
    val content: String,
    val images: List<String> = emptyList(),
    val error: ChatError? = null,
    val kind: MessageKind = MessageKind.CHAT,
    val sources: List<SourceRef> = emptyList(),
    val searching: Boolean = false,
    /** Deep Research 任务 id（随占位消息持久化——冷启动恢复轮询的载体） */
    val researchRunId: String? = null,
)
