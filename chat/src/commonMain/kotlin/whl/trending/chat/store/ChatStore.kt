package whl.trending.chat.store

import kotlinx.coroutines.flow.Flow
import whl.trending.chat.ChatContext
import whl.trending.chat.ThreadSummary
import whl.trending.chat.model.ChatError
import whl.trending.chat.model.ChatMessage
import whl.trending.chat.model.MessageKind
import whl.trending.chat.model.SourceRef

/** 跨进程恢复轮询的载体：库里「空内容 + runId + 无 error」的 research 占位行 */
data class PendingResearch(val threadId: Long, val messageId: Long, val runId: String)

/**
 * 会话持久化契约。消息 id 全局唯一且由 store 分配（Room 实现即行 id）——
 * VM 侧不再维护第二套 id 序列，所有 append* 方法返回带最终 id 的消息。
 *
 * 落库策略（EchoFlow 经验的裁剪版，见 ai-chat/chat-改造评估方案-v2.md P1）：
 * - 懒建：进入 chat 不落库，首条消息发出时才建 thread；
 * - user 消息即发即落；assistant 成功终局落一次（流式过程零写库）；
 * - error 也落库（重启后错误条仍可见可重试，且 id 空间保持单一）；
 * - research 占位行提交成功即落（跨进程恢复轮询的载体）。
 */
interface ChatStore {

    fun threads(): Flow<List<ThreadSummary>>

    /** 总是新建（入口进入与抽屉「新会话」同语义：不复用历史） */
    suspend fun createThread(context: ChatContext?, firstMessageText: String): Long

    /** 会话的 ChatContext（解读 chip 与服务端 context 注入依赖）；通用入口/解析失败为 null */
    suspend fun contextOf(threadId: Long): ChatContext?

    suspend fun loadMessages(threadId: Long): List<ChatMessage>

    suspend fun renameThread(threadId: Long, title: String)

    suspend fun deleteThread(threadId: Long)

    /** user 消息落库；图片迁入持久目录后路径可能改写，以返回值为准 */
    suspend fun appendUserMessage(
        threadId: Long,
        text: String,
        images: List<String> = emptyList(),
        kind: MessageKind = MessageKind.CHAT,
    ): ChatMessage

    suspend fun appendAssistantMessage(
        threadId: Long,
        content: String,
        kind: MessageKind,
        model: String?,
        sources: List<SourceRef> = emptyList(),
    ): ChatMessage

    /** 失败终局落一条错误行；research 提交失败时无 runId */
    suspend fun appendErrorMessage(
        threadId: Long,
        kind: MessageKind,
        error: ChatError,
        researchRunId: String? = null,
    ): ChatMessage

    /** research 占位（空内容 + runId） */
    suspend fun appendResearchPlaceholder(threadId: Long, runId: String): ChatMessage

    /** research 终局：占位行升级为报告全文（保留 runId 供追溯；model 为生成模型留痕） */
    suspend fun completeResearch(threadId: Long, messageId: Long, report: String, runId: String, model: String?)

    /**
     * research 失败写回占位行。[runId] 非空表示任务在服务端仍可续（重试恢复轮询、
     * 不重复扣费）；null 表示终局死亡（服务端已退款），行不再触发恢复轮询。
     */
    suspend fun markResearchError(threadId: Long, messageId: Long, error: ChatError, runId: String?)

    /** 重试续轮前清掉错误标记，行回到占位形态 */
    suspend fun resetResearchPlaceholder(threadId: Long, messageId: Long, runId: String)

    /** 重试重新提交前移除错误行 */
    suspend fun deleteMessage(messageId: Long)

    suspend fun pendingResearch(): List<PendingResearch>

    companion object {
        const val MAX_TITLE_LENGTH = 20
        const val DEFAULT_TITLE = "新对话"
        const val ENTRY_GENERAL = "general"

        /** 入口键：repo 条目按 externalId 隔离，其余（含 HN/PH 无 externalId 的场景）归通用 */
        fun entryKeyOf(context: ChatContext?): String =
            context?.externalId?.let { "repo:$it" } ?: ENTRY_GENERAL

        internal fun titleFrom(text: String): String =
            text.trim().take(MAX_TITLE_LENGTH).ifBlank { DEFAULT_TITLE }
    }
}
