package whl.trending.chat.store

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import whl.trending.chat.ThreadSummary
import whl.trending.chat.core.epochMillis
import whl.trending.chat.db.ChatDatabase
import whl.trending.chat.db.MessageEntity
import whl.trending.chat.db.ThreadEntity
import whl.trending.chat.model.ChatError
import whl.trending.chat.model.ChatErrorCategory
import whl.trending.chat.model.ChatMessage
import whl.trending.chat.model.Role
import whl.trending.chat.model.SourceRef
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Room 实现。图片落库时从 cacheDir 拷入 [imagesDir]（filesDir 下，系统不清理），
 * 删线程先删文件再删行。error 与搜索来源存 segmentsJson 信封，不改表结构。
 *
 * @param clock 时间注入点（测试替身）；updatedAt 排序与懒建时间戳共用
 */
class RoomChatStore(
    private val db: ChatDatabase,
    private val imagesDir: String,
    private val clock: () -> Long = { epochMillis() },
) : ChatStore {

    override fun threads(): Flow<List<ThreadSummary>> =
        db.threadDao().observeAll().map { list -> list.map { ThreadSummary(it.id, it.title, it.updatedAt) } }

    override suspend fun createThread(firstMessageText: String): Long {
        val now = clock()
        return db.threadDao().insert(
            ThreadEntity(
                title = ChatStore.titleFrom(firstMessageText),
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    /** 空内容且无错误的 assistant 行不外发：已下线的 research 占位可能残留库中 */
    override suspend fun loadMessages(threadId: Long): List<ChatMessage> =
        db.messageDao().messagesFor(threadId).map { it.toModel() }
            .filter { it.role == Role.USER || it.content.isNotBlank() || it.error != null }

    override suspend fun renameThread(threadId: Long, title: String) =
        db.threadDao().rename(threadId, title.trim().take(ChatStore.MAX_TITLE_LENGTH))

    /** 删线程：先删图片文件（CASCADE 之后路径就找不回了），再删行。
     *  只删 [imagesDir] 名下的文件——迁移失败的行留着源路径（copyIntoStore 的回退），
     *  顺着删会伤及 store 之外的文件 */
    override suspend fun deleteThread(threadId: Long) {
        val dir = imagesDir.toPath()
        db.messageDao().imagesJsonFor(threadId).forEach { raw ->
            runCatching { json.decodeFromString<List<String>>(raw) }.getOrNull()
                ?.forEach { path ->
                    val p = path.toPath()
                    if (p.parent == dir) runCatching { FileSystem.SYSTEM.delete(p) }
                }
        }
        db.threadDao().delete(threadId)
    }

    override suspend fun appendUserMessage(
        threadId: Long,
        text: String,
        images: List<String>,
    ): ChatMessage {
        val persistedImages = images.map { copyIntoStore(it) }
        val id = db.messageDao().insert(
            MessageEntity(
                threadId = threadId,
                role = ROLE_USER,
                content = text,
                imagesJson = persistedImages.takeIf { it.isNotEmpty() }?.let { json.encodeToString(it) },
                model = null,
                segmentsJson = null,
                createdAt = clock(),
            ),
        )
        db.threadDao().touch(threadId, clock())
        return ChatMessage(id = id, role = Role.USER, content = text, images = persistedImages)
    }

    override suspend fun appendAssistantMessage(
        threadId: Long,
        content: String,
        model: String?,
        sources: List<SourceRef>,
    ): ChatMessage {
        val segments = sources.takeIf { it.isNotEmpty() }
            ?.let { json.encodeToString(StoredSegments(sources = it.map { s -> StoredSource(s.title, s.url) })) }
        val id = db.messageDao().insert(
            MessageEntity(
                threadId = threadId, role = ROLE_ASSISTANT, content = content,
                imagesJson = null, model = model,
                segmentsJson = segments, createdAt = clock(),
            ),
        )
        db.threadDao().touch(threadId, clock())
        return ChatMessage(id = id, role = Role.ASSISTANT, content = content, sources = sources, model = model)
    }

    override suspend fun appendErrorMessage(threadId: Long, error: ChatError): ChatMessage {
        val id = db.messageDao().insert(
            MessageEntity(
                threadId = threadId, role = ROLE_ASSISTANT, content = "",
                imagesJson = null, model = null,
                segmentsJson = json.encodeToString(StoredSegments(error = StoredError.from(error))),
                createdAt = clock(),
            ),
        )
        db.threadDao().touch(threadId, clock())
        return ChatMessage(id = id, role = Role.ASSISTANT, content = "", error = error)
    }

    override suspend fun deleteMessage(messageId: Long) = db.messageDao().deleteById(messageId)

    // 内部

    @OptIn(ExperimentalUuidApi::class)
    private fun copyIntoStore(sourcePath: String): String {
        val fs = FileSystem.SYSTEM
        val source = sourcePath.toPath()
        val dir = imagesDir.toPath()
        if (!fs.exists(source) || source.parent == dir) return sourcePath
        // 保留源扩展名（当前附件层恒产 JPEG，此处是对未来透传原图的加固；无扩展名回退 jpg）
        val extension = source.name.substringAfterLast('.', "").takeIf { it.isNotBlank() } ?: "jpg"
        val target = dir / "${Uuid.random()}.$extension"
        return runCatching {
            fs.createDirectories(dir)
            fs.copy(source, target)
            target.toString()
        }.getOrDefault(sourcePath) // 拷贝失败退回原路径：宁可将来图裂，不阻塞发送
    }

    private fun decodeSegments(raw: String): StoredSegments? =
        runCatching { json.decodeFromString<StoredSegments>(raw) }.getOrNull()

    private fun MessageEntity.toModel(): ChatMessage {
        val segments = segmentsJson?.let { decodeSegments(it) }
        return ChatMessage(
            id = id,
            role = if (role == ROLE_USER) Role.USER else Role.ASSISTANT,
            content = content,
            images = imagesJson?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrNull() }
                ?: emptyList(),
            error = segments?.error?.toError(),
            sources = segments?.sources?.map { SourceRef(it.title, it.url) } ?: emptyList(),
            model = model,
        )
    }

    /**
     * segmentsJson 信封 v1：搜索来源 / 错误终局共用；v 字段为演进留位，
     * 反序列化 ignoreUnknownKeys 保证老版本读新数据不崩（EchoFlow 教训的版本化版）。
     * 旧行可能含已退役字段（如 researchRunId），同样被直接丢弃。
     */
    @Serializable
    private data class StoredSegments(
        val v: Int = 1,
        val sources: List<StoredSource> = emptyList(),
        val error: StoredError? = null,
    )

    @Serializable
    private data class StoredSource(val title: String, val url: String)

    @Serializable
    private data class StoredError(
        val category: String,
        val code: String? = null,
        val httpStatus: Int? = null,
        val detail: String? = null,
        val tier: String? = null,
        val authDegraded: Boolean = false,
    ) {
        fun toError() = ChatError(
            category = runCatching { ChatErrorCategory.valueOf(category) }.getOrDefault(ChatErrorCategory.UNKNOWN),
            code = code, httpStatus = httpStatus, detail = detail, tier = tier, authDegraded = authDegraded,
        )

        companion object {
            fun from(e: ChatError) = StoredError(
                category = e.category.name, code = e.code, httpStatus = e.httpStatus,
                detail = e.detail, tier = e.tier, authDegraded = e.authDegraded,
            )
        }
    }

    private companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
        val json = Json { ignoreUnknownKeys = true }
    }
}
