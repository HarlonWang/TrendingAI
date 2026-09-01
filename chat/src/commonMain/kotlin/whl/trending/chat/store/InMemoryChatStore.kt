package whl.trending.chat.store

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import whl.trending.chat.ThreadSummary
import whl.trending.chat.model.ChatError
import whl.trending.chat.model.ChatMessage
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
    ): ChatMessage = append(threadId, ChatMessage(0, Role.USER, text, images = images))

    override suspend fun appendAssistantMessage(
        threadId: Long,
        content: String,
        model: String?,
        sources: List<SourceRef>,
    ): ChatMessage = append(threadId, ChatMessage(0, Role.ASSISTANT, content, sources = sources, model = model))

    override suspend fun appendErrorMessage(threadId: Long, error: ChatError): ChatMessage =
        append(threadId, ChatMessage(0, Role.ASSISTANT, "", error = error))

    override suspend fun deleteMessage(messageId: Long) {
        messagesByThread.values.forEach { list -> list.removeAll { it.id == messageId } }
    }

    private fun append(threadId: Long, message: ChatMessage): ChatMessage {
        val stored = message.copy(id = ++nextMessageId, searching = false)
        messagesByThread.getOrPut(threadId) { mutableListOf() }.add(stored)
        threads[threadId]?.updatedAt = ++tick
        publish()
        return stored
    }

    private fun publish() {
        threadsFlow.value = threads.values
            .sortedByDescending { it.updatedAt }
            .map { ThreadSummary(it.id, it.title, it.updatedAt) }
    }
}
