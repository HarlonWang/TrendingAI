package whl.trending.chat.store

import kotlinx.coroutines.flow.Flow
import whl.trending.chat.ThreadSummary
import whl.trending.chat.model.ChatError
import whl.trending.chat.model.ChatMessage
import whl.trending.chat.model.SourceRef

/**
 * 会话持久化契约。消息 id 全局唯一且由 store 分配（Room 实现即行 id）——
 * VM 侧不再维护第二套 id 序列，所有 append* 方法返回带最终 id 的消息。
 *
 * 落库策略（EchoFlow 经验的裁剪版，见 ai-chat/chat-改造评估方案-v2.md P1）：
 * - 懒建：进入 chat 不落库，首条消息发出时才建 thread；
 * - user 消息即发即落；assistant 成功终局落一次（流式过程零写库）；
 * - error 也落库（重启后错误条仍可见可重试，且 id 空间保持单一）。
 */
interface ChatStore {

    fun threads(): Flow<List<ThreadSummary>>

    /** 总是新建，标题取首条消息截断（不复用历史） */
    suspend fun createThread(firstMessageText: String): Long

    suspend fun loadMessages(threadId: Long): List<ChatMessage>

    suspend fun renameThread(threadId: Long, title: String)

    suspend fun deleteThread(threadId: Long)

    /** user 消息落库；图片迁入持久目录后路径可能改写，以返回值为准 */
    suspend fun appendUserMessage(
        threadId: Long,
        text: String,
        images: List<String> = emptyList(),
    ): ChatMessage

    suspend fun appendAssistantMessage(
        threadId: Long,
        content: String,
        model: String?,
        sources: List<SourceRef> = emptyList(),
    ): ChatMessage

    /** 失败终局落一条错误行 */
    suspend fun appendErrorMessage(threadId: Long, error: ChatError): ChatMessage

    /** 重试重新提交前移除错误行 */
    suspend fun deleteMessage(messageId: Long)

    companion object {
        const val MAX_TITLE_LENGTH = 20
        const val DEFAULT_TITLE = "新对话"

        internal fun titleFrom(text: String): String =
            text.trim().take(MAX_TITLE_LENGTH).ifBlank { DEFAULT_TITLE }
    }
}
