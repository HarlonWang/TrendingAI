package whl.trending.chat.store

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import whl.trending.chat.ThreadSummary
import whl.trending.chat.model.ChatError
import whl.trending.chat.model.ChatMessage
import whl.trending.chat.model.MessageKind
import whl.trending.chat.model.Role
import whl.trending.chat.model.SourceRef

/**
 * 纯内存实现：Demo / 预览 / 无持久化诉求的宿主用。语义与 Room 实现一致
 * （id 全局唯一、错误行保留），进程结束即消失。
 */
class InMemoryChatStore : ChatStore {

    private class Thread(
        val id: Long,
        var title: String,
        var updatedAt: Long,
    )

    private var nextThreadId = 0L
    private var nextMessageId = 0L
    private var tick = 0L
    private val threads = LinkedHashMap<Long, Thread>()
    private val messagesByThread = LinkedHashMap<Long, MutableList<ChatMessage>>()
    private val threadsFlow = MutableStateFlow<List<ThreadSummary>>(emptyList())

    override fun threads(): Flow<List<ThreadSummary>> = threadsFlow

    override suspend fun createThread(firstMessageText: String): Long {
        val id = ++nextThreadId
        threads[id] = Thread(id, ChatStore.titleFrom(firstMessageText), ++tick)
        messagesByThread[id] = mutableListOf()
        publish()
        return id
    }

    override suspend fun loadMessages(threadId: Long): List<ChatMessage> =
        messagesByThread[threadId]?.toList() ?: emptyList()

    override suspend fun renameThread(threadId: Long, title: String) {
        threads[threadId]?.title = title.trim().take(ChatStore.MAX_TITLE_LENGTH)
        publish()
    }

    override suspend fun deleteThread(threadId: Long) {
        threads.remove(threadId)
        messagesByThread.remove(threadId)
        publish()
    }

    override suspend fun appendUserMessage(
        threadId: Long,
        text: String,
        images: List<String>,
        kind: MessageKind,
    ): ChatMessage = append(threadId, ChatMessage(0, Role.USER, text, images = images, kind = kind))

    override suspend fun appendAssistantMessage(
        threadId: Long,
        content: String,
        kind: MessageKind,
        model: String?,
        sources: List<SourceRef>,
    ): ChatMessage = append(threadId, ChatMessage(0, Role.ASSISTANT, content, kind = kind, sources = sources, model = model))

    override suspend fun appendErrorMessage(
        threadId: Long,
        kind: MessageKind,
        error: ChatError,
        researchRunId: String?,
    ): ChatMessage = append(threadId, ChatMessage(0, Role.ASSISTANT, "", error = error, kind = kind, researchRunId = researchRunId))

    override suspend fun appendResearchPlaceholder(threadId: Long, runId: String): ChatMessage =
        append(threadId, ChatMessage(0, Role.ASSISTANT, "", kind = MessageKind.DEEP_RESEARCH, researchRunId = runId))

    override suspend fun completeResearch(threadId: Long, messageId: Long, report: String, runId: String, model: String?) =
        update(threadId, messageId) { it.copy(content = report, error = null, researchRunId = runId, model = model) }

    override suspend fun markResearchError(threadId: Long, messageId: Long, error: ChatError, runId: String?) =
        update(threadId, messageId) { it.copy(content = "", error = error, researchRunId = runId) }

    override suspend fun resetResearchPlaceholder(threadId: Long, messageId: Long, runId: String) =
        update(threadId, messageId) { it.copy(content = "", error = null, researchRunId = runId) }

    override suspend fun deleteMessage(messageId: Long) {
        messagesByThread.values.forEach { list -> list.removeAll { it.id == messageId } }
    }

    override suspend fun pendingResearch(): List<PendingResearch> =
        messagesByThread.flatMap { (threadId, list) ->
            list.filter { it.kind == MessageKind.DEEP_RESEARCH && it.content.isBlank() && it.error == null }
                .mapNotNull { m -> m.researchRunId?.let { PendingResearch(threadId, m.id, it) } }
        }

    private fun append(threadId: Long, message: ChatMessage): ChatMessage {
        val stored = message.copy(id = ++nextMessageId, searching = false)
        messagesByThread.getOrPut(threadId) { mutableListOf() }.add(stored)
        threads[threadId]?.updatedAt = ++tick
        publish()
        return stored
    }

    private fun update(threadId: Long, messageId: Long, transform: (ChatMessage) -> ChatMessage) {
        val list = messagesByThread[threadId] ?: return
        val index = list.indexOfFirst { it.id == messageId }
        if (index >= 0) list[index] = transform(list[index])
        threads[threadId]?.updatedAt = ++tick
        publish()
    }

    private fun publish() {
        threadsFlow.value = threads.values
            .sortedByDescending { it.updatedAt }
            .map { ThreadSummary(it.id, it.title, it.updatedAt) }
    }
}
