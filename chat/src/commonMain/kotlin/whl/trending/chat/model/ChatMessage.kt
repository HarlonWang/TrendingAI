package whl.trending.chat.model

/** 消息角色 */
enum class Role { USER, ASSISTANT }

/**
 * 消息由哪条管线生成：驱动 retry 路由，渲染无差别。
 * 随消息持久化（kind 列存枚举名），新增值向后兼容、改名不兼容；
 * 历史值 DETAIL_SUMMARY（已退役的「一键解读」）由加载侧回落 CHAT。
 */
enum class MessageKind { CHAT, DEEP_RESEARCH }

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
    /** 生成本条消息的模型 id（research 由服务端随报告返回；气泡上标注用），可空 */
    val model: String? = null,
)
