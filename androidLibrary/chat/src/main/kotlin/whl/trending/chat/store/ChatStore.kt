package whl.trending.chat.store

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import whl.trending.ai.chat.ChatContext
import whl.trending.chat.db.ChatDatabase
import whl.trending.chat.db.MessageEntity
import whl.trending.chat.db.ThreadEntity
import whl.trending.chat.model.ChatMessage
import whl.trending.chat.model.MessageKind
import whl.trending.chat.model.Role
import whl.trending.chat.model.SourceRef
import java.io.File
import java.util.UUID

/**
 * 会话持久化层。落库策略（EchoFlow 经验的裁剪版，见 ai-chat/chat-改造评估方案-v2.md P1）：
 * - 懒建：进入 chat 不落库，首条消息发出时才建 thread；
 * - 终局一次写：流式过程零写库，user 消息即发即落，assistant 仅成功终局落一次，
 *   error 消息不落（瞬态可重试，重启后无意义）；
 * - 图片落库时从 cacheDir 拷入 [imagesDir]（filesDir 下，系统不清理），删线程先删文件再删行。
 *
 * @param clock 时间注入点（测试替身）；updatedAt 排序与懒建时间戳共用
 */
class ChatStore(
    private val db: ChatDatabase,
    private val imagesDir: File,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    /** 入口恢复语义：该入口最近活跃的会话 id；无历史返回 null（UI 呈现空态，不落库） */
    suspend fun resolveLatestThread(context: ChatContext?): Long? =
        db.threadDao().latestByEntry(entryKeyOf(context))?.id

    /**
     * 懒建或复用：同入口已有会话直接复用最近的，否则以首条消息建线。
     * 标题优先取 context 标题（repo 名可读性最好），通用入口取首条消息截断。
     */
    suspend fun ensureThread(context: ChatContext?, firstMessageText: String): Long {
        db.threadDao().latestByEntry(entryKeyOf(context))?.let { return it.id }
        return createThread(context, firstMessageText)
    }

    /** 总是新建（抽屉「新会话」语义）——入口复用由 [resolveLatestThread] 在进入时单独承担 */
    suspend fun createThread(context: ChatContext?, firstMessageText: String): Long {
        val now = clock()
        return db.threadDao().insert(
            ThreadEntity(
                title = context?.title ?: titleFrom(firstMessageText),
                entryKey = entryKeyOf(context),
                contextJson = context?.let { json.encodeToString(StoredContext.from(it)) },
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    /** 恢复会话的 ChatContext（解读 chip 与服务端 context 注入依赖）；通用入口/解析失败为 null */
    suspend fun contextOf(threadId: Long): ChatContext? =
        db.threadDao().getById(threadId)?.contextJson?.let { raw ->
            runCatching { json.decodeFromString<StoredContext>(raw).toContext() }.getOrNull()
        }

    /** user 消息即发即落；图片从 cacheDir 拷入 filesDir 并回写新路径（cache 可被系统清理） */
    suspend fun persistUserMessage(threadId: Long, message: ChatMessage): ChatMessage {
        val persistedImages = message.images.map { copyIntoStore(it) }
        val stored = message.copy(images = persistedImages)
        db.messageDao().insert(stored.toEntity(threadId, clock()))
        db.threadDao().touch(threadId, clock())
        return stored
    }

    /** assistant 仅成功终局落一次；空内容（如内容过滤拒答）不落空行 */
    suspend fun persistAssistantMessage(
        threadId: Long,
        content: String,
        kind: MessageKind,
        model: String?,
        sources: List<SourceRef> = emptyList(),
    ) {
        if (content.isBlank()) return
        db.messageDao().insert(
            MessageEntity(
                threadId = threadId,
                role = ROLE_ASSISTANT,
                content = content,
                imagesJson = null,
                kind = kind.name,
                model = model,
                segmentsJson = sources.takeIf { it.isNotEmpty() }
                    ?.let { json.encodeToString(StoredSegments(sources = it.map { s -> StoredSource(s.title, s.url) })) },
                createdAt = clock(),
            ),
        )
        db.threadDao().touch(threadId, clock())
    }

    suspend fun loadMessages(threadId: Long): List<ChatMessage> =
        db.messageDao().messagesFor(threadId).map { it.toModel() }

    /**
     * Deep Research 占位行：提交成功即落库（content 空 + runId），是跨进程恢复轮询的
     * 载体——进程死亡后重进，「空内容 + runId」的行触发续轮询，5 credits 不白花。
     */
    suspend fun persistResearchPlaceholder(threadId: Long, runId: String): Long {
        val id = db.messageDao().insert(
            MessageEntity(
                threadId = threadId, role = ROLE_ASSISTANT, content = "",
                imagesJson = null, kind = MessageKind.DEEP_RESEARCH.name, model = null,
                segmentsJson = json.encodeToString(StoredSegments(researchRunId = runId)),
                createdAt = clock(),
            ),
        )
        db.threadDao().touch(threadId, clock())
        return id
    }

    /** research 终局：占位行升级为报告全文（保留 runId 供追溯） */
    suspend fun completeResearchMessage(threadId: Long, messageId: Long, report: String, runId: String) {
        db.messageDao().updateContent(
            messageId, report,
            json.encodeToString(StoredSegments(researchRunId = runId)),
        )
        db.threadDao().touch(threadId, clock())
    }

    /** research 失败：删占位行（服务端已退款；留着会让重启误续轮询死任务） */
    suspend fun deleteMessage(messageId: Long) = db.messageDao().deleteById(messageId)

    suspend fun renameThread(threadId: Long, title: String) =
        db.threadDao().rename(threadId, title.trim().take(MAX_TITLE_LENGTH))

    /** 删线程：先删图片文件（CASCADE 之后路径就找不回了），再删行 */
    suspend fun deleteThread(threadId: Long) {
        db.messageDao().imagesJsonFor(threadId).forEach { raw ->
            runCatching { json.decodeFromString<List<String>>(raw) }.getOrNull()
                ?.forEach { path -> File(path).delete() }
        }
        db.threadDao().delete(threadId)
    }

    fun threads() = db.threadDao().observeAll()

    // ---- 内部 ----

    private fun copyIntoStore(sourcePath: String): String {
        val source = File(sourcePath)
        if (!source.exists() || source.parentFile?.absolutePath == imagesDir.absolutePath) return sourcePath
        imagesDir.mkdirs()
        // 保留源扩展名（当前附件层恒产 JPEG，此处是对未来透传原图的加固；无扩展名回退 jpg）
        val extension = source.extension.takeIf { it.isNotBlank() } ?: "jpg"
        val target = File(imagesDir, "${UUID.randomUUID()}.$extension")
        return runCatching {
            source.copyTo(target, overwrite = true)
            target.absolutePath
        }.getOrDefault(sourcePath) // 拷贝失败退回原路径：宁可将来图裂，不阻塞发送
    }

    private fun titleFrom(text: String): String =
        text.trim().take(MAX_TITLE_LENGTH).ifBlank { DEFAULT_TITLE }

    private fun MessageEntity.toModel(): ChatMessage {
        // segments 信封热路径只解码一次（Sourcery 建议）
        val segments = segmentsJson?.let { raw ->
            runCatching { json.decodeFromString<StoredSegments>(raw) }.getOrNull()
        }
        return ChatMessage(
            id = id,
            role = if (role == ROLE_USER) Role.USER else Role.ASSISTANT,
            content = content,
            images = imagesJson?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrNull() }
                ?: emptyList(),
            kind = runCatching { MessageKind.valueOf(kind) }.getOrDefault(MessageKind.CHAT),
            sources = segments?.sources?.map { SourceRef(it.title, it.url) } ?: emptyList(),
            researchRunId = segments?.researchRunId,
        )
    }

    private fun ChatMessage.toEntity(threadId: Long, now: Long) = MessageEntity(
        threadId = threadId,
        role = if (role == Role.USER) ROLE_USER else ROLE_ASSISTANT,
        content = content,
        imagesJson = images.takeIf { it.isNotEmpty() }?.let { json.encodeToString(it) },
        kind = kind.name,
        model = null,
        segmentsJson = null,
        createdAt = now,
    )

    /**
     * segmentsJson 信封 v1（P2）：目前只承载搜索来源；v 字段为演进留位，
     * 反序列化 ignoreUnknownKeys 保证老版本读新数据不崩（EchoFlow 教训的版本化版）
     */
    @Serializable
    private data class StoredSegments(val v: Int = 1, val sources: List<StoredSource> = emptyList(), val researchRunId: String? = null)

    @Serializable
    private data class StoredSource(val title: String, val url: String)

    /**
     * ChatContext 的持久化镜像（shared 里的原类未标 @Serializable，此处 DTO 隔离序列化关注点）。
     * autoDetailSummary 是一次性触发标记，不持久化。
     */
    @Serializable
    private data class StoredContext(
        val title: String,
        val summary: String? = null,
        val sourceUrl: String? = null,
        val source: String? = null,
        val externalId: String? = null,
        val readmeLength: Int? = null,
    ) {
        fun toContext() = ChatContext(
            title = title, summary = summary, sourceUrl = sourceUrl,
            source = source, externalId = externalId, readmeLength = readmeLength,
        )

        companion object {
            fun from(c: ChatContext) = StoredContext(
                title = c.title, summary = c.summary, sourceUrl = c.sourceUrl,
                source = c.source, externalId = c.externalId, readmeLength = c.readmeLength,
            )
        }
    }

    companion object {
        const val MAX_TITLE_LENGTH = 20
        const val DEFAULT_TITLE = "新对话"
        const val ENTRY_GENERAL = "general"
        private const val ROLE_USER = "user"
        private const val ROLE_ASSISTANT = "assistant"

        private val json = Json { ignoreUnknownKeys = true }

        /** 入口键：repo 条目按 externalId 隔离，其余（含 HN/PH 无 externalId 的场景）归通用 */
        fun entryKeyOf(context: ChatContext?): String =
            context?.externalId?.let { "repo:$it" } ?: ENTRY_GENERAL
    }
}
