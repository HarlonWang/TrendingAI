package whl.trending.chat.store

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import whl.trending.chat.ChatContext
import whl.trending.chat.db.ChatDatabase
import whl.trending.chat.model.ChatError
import whl.trending.chat.model.ChatErrorCategory
import whl.trending.chat.model.MessageKind
import whl.trending.chat.model.Role
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Room 持久化层行为：建线与标题策略 / context 往返 / 图片迁移 filesDir /
 * 错误行落库往返 / research 占位与 pendingResearch 哨兵 / 删除先删文件。
 * sdk 钉 35：同 ChatDatabaseTest（Robolectric 4.16.x + 测试 JVM 17 的上限）
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChatStoreTest {

    private lateinit var db: ChatDatabase
    private lateinit var imagesDir: File
    private lateinit var cacheDir: File
    private var now = 1000L
    private lateinit var store: RoomChatStore

    private val repoContext = ChatContext(
        title = "octo/demo",
        summary = "A demo repo",
        sourceUrl = "https://github.com/octo/demo",
        source = "github",
        externalId = "octo/demo",
        readmeLength = 2000,
    )

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ChatDatabase::class.java,
        ).build()
        imagesDir = File.createTempFile("imgs", null).apply { delete(); mkdirs() }
        cacheDir = File.createTempFile("cache", null).apply { delete(); mkdirs() }
        store = RoomChatStore(db, imagesDir.absolutePath, clock = { now })
    }

    @After
    fun teardown() {
        db.close()
        imagesDir.deleteRecursively()
        cacheDir.deleteRecursively()
    }

    private fun cacheImage(name: String): String =
        File(cacheDir, name).apply { writeBytes(byteArrayOf(1, 2, 3)) }.absolutePath

    // 建线与标题策略

    @Test
    fun `repo 入口建线：标题取 context 标题，entryKey 为 repo 前缀`() = runTest {
        val id = store.createThread(repoContext, firstMessageText = "介绍一下")
        val thread = db.threadDao().getById(id)!!
        assertEquals("octo/demo", thread.title)
        assertEquals("repo:octo/demo", thread.entryKey)
    }

    @Test
    fun `通用入口标题取首条消息，超长截断`() = runTest {
        val id = store.createThread(null, firstMessageText = "这是一条非常非常非常非常非常非常长的首条消息内容")
        val title = db.threadDao().getById(id)!!.title
        assertTrue(title.length <= ChatStore.MAX_TITLE_LENGTH)
        assertTrue(title.startsWith("这是一条"))
    }

    @Test
    fun `纯图消息无文本 → 默认标题`() = runTest {
        val id = store.createThread(null, firstMessageText = "")
        assertEquals(ChatStore.DEFAULT_TITLE, db.threadDao().getById(id)!!.title)
    }

    // context 往返

    @Test
    fun `contextJson 往返：恢复的会话还原 ChatContext 关键字段`() = runTest {
        val id = store.createThread(repoContext, firstMessageText = "hi")
        val restored = store.contextOf(id)!!
        assertEquals("octo/demo", restored.title)
        assertEquals("github", restored.source)
        assertEquals("octo/demo", restored.externalId)
        assertEquals(2000, restored.readmeLength)
        // autoDetailSummary 是一次性触发标记，不持久化
        assertFalse(restored.autoDetailSummary)
    }

    // 消息落库

    @Test
    fun `appendUserMessage 把图片从 cache 拷入 filesDir 并回写新路径，id 即行 id`() = runTest {
        val cachePath = cacheImage("a.jpg")
        val id = store.createThread(null, firstMessageText = "看图")
        val persisted = store.appendUserMessage(id, "看图", images = listOf(cachePath))

        assertEquals(1, persisted.images.size)
        val newPath = persisted.images[0]
        assertTrue(newPath.startsWith(imagesDir.absolutePath))
        assertTrue(File(newPath).exists())

        val rows = db.messageDao().messagesFor(id)
        assertEquals(1, rows.size)
        assertEquals(rows[0].id, persisted.id)
        assertTrue(rows[0].imagesJson!!.contains(newPath))
    }

    @Test
    fun `loadMessages 完整还原 model 层字段（kind、model 与图片）`() = runTest {
        val id = store.createThread(repoContext, firstMessageText = "解读")
        store.appendUserMessage(id, "解读", kind = MessageKind.DETAIL_SUMMARY)
        store.appendAssistantMessage(id, "解读内容", MessageKind.DETAIL_SUMMARY, model = "gpt-5.5")

        val loaded = store.loadMessages(id)
        assertEquals(2, loaded.size)
        assertEquals(MessageKind.DETAIL_SUMMARY, loaded[0].kind)
        assertEquals(Role.ASSISTANT, loaded[1].role)
        assertEquals("解读内容", loaded[1].content)
        assertEquals("gpt-5.5", loaded[1].model)
        assertTrue(loaded[1].id > loaded[0].id)
    }

    @Test
    fun `错误行落库往返：分类、机器码、tier、authDegraded 全还原`() = runTest {
        val id = store.createThread(null, firstMessageText = "hi")
        store.appendUserMessage(id, "hi")
        val error = ChatError(
            ChatErrorCategory.QUOTA,
            code = ChatError.CODE_QUOTA_DEVICE,
            httpStatus = 429,
            detail = "quota exceeded",
            tier = ChatError.TIER_ANONYMOUS,
            authDegraded = true,
        )
        store.appendErrorMessage(id, MessageKind.CHAT, error)

        val loaded = store.loadMessages(id).last()
        assertEquals("", loaded.content)
        assertEquals(error, loaded.error)
        assertEquals(MessageKind.CHAT, loaded.kind)
    }

    // research 占位与恢复哨兵

    @Test
    fun `research 占位进 pendingResearch，完成后退出`() = runTest {
        val id = store.createThread(null, firstMessageText = "研究")
        val placeholder = store.appendResearchPlaceholder(id, runId = "run-1")

        assertEquals(
            listOf(PendingResearch(id, placeholder.id, "run-1")),
            store.pendingResearch(),
        )

        store.completeResearch(id, placeholder.id, "# 报告", "run-1", model = "gpt-5.5")
        assertTrue(store.pendingResearch().isEmpty())
        val loaded = store.loadMessages(id).last()
        assertEquals("# 报告", loaded.content)
        assertEquals("run-1", loaded.researchRunId)
        assertEquals("gpt-5.5", loaded.model)
    }

    @Test
    fun `markResearchError 后不再 pending；保留 runId 时 reset 可恢复占位形态`() = runTest {
        val id = store.createThread(null, firstMessageText = "研究")
        val placeholder = store.appendResearchPlaceholder(id, runId = "run-1")

        // runId 保留（轮询永久错误）：错误行不触发恢复轮询，但重试可续
        store.markResearchError(id, placeholder.id, ChatError(ChatErrorCategory.TIMEOUT), runId = "run-1")
        assertTrue(store.pendingResearch().isEmpty())
        val errored = store.loadMessages(id).last()
        assertEquals(ChatErrorCategory.TIMEOUT, errored.error?.category)
        assertEquals("run-1", errored.researchRunId)

        now = 2000L
        store.resetResearchPlaceholder(id, placeholder.id, "run-1")
        assertEquals(listOf(PendingResearch(id, placeholder.id, "run-1")), store.pendingResearch())
        assertNull(store.loadMessages(id).last().error)
        // 会话冒泡：reset 与其他写路径一样 touch 线程
        assertEquals(2000L, db.threadDao().getById(id)!!.updatedAt)
    }

    @Test
    fun `markResearchError 判死（runId 置空）：错误行既不 pending 也无 runId`() = runTest {
        val id = store.createThread(null, firstMessageText = "研究")
        val placeholder = store.appendResearchPlaceholder(id, runId = "run-1")
        store.markResearchError(id, placeholder.id, ChatError(ChatErrorCategory.SERVER), runId = null)

        assertTrue(store.pendingResearch().isEmpty())
        val loaded = store.loadMessages(id).last()
        assertNull(loaded.researchRunId)
        assertEquals(ChatErrorCategory.SERVER, loaded.error?.category)
    }

    // 删除

    @Test
    fun `deleteThread 先删图片文件再删行`() = runTest {
        val cachePath = cacheImage("b.jpg")
        val id = store.createThread(null, firstMessageText = "看图")
        val persisted = store.appendUserMessage(id, "看图", images = listOf(cachePath))
        val storedFile = File(persisted.images[0])
        assertTrue(storedFile.exists())

        store.deleteThread(id)

        assertFalse(storedFile.exists())
        assertNull(db.threadDao().getById(id))
        assertTrue(db.messageDao().messagesFor(id).isEmpty())
    }

    @Test
    fun `deleteThread 不删 store 目录之外的文件（迁移失败回退的源路径）`() = runTest {
        // 发送时源文件不存在 → copyIntoStore 跳过迁移、原路径入库（失败回退分支）
        val outside = File(cacheDir, "keep.jpg").absolutePath
        val id = store.createThread(null, firstMessageText = "看图")
        val persisted = store.appendUserMessage(id, "看图", images = listOf(outside))
        assertEquals(outside, persisted.images[0])

        File(outside).writeBytes(byteArrayOf(1)) // 文件随后出现在 store 之外

        store.deleteThread(id)

        assertTrue(File(outside).exists()) // 只删 imagesDir 名下的文件
        assertNull(db.threadDao().getById(id))
    }

    @Test
    fun `deleteMessage 只删目标行`() = runTest {
        val id = store.createThread(null, firstMessageText = "hi")
        store.appendUserMessage(id, "hi")
        val error = store.appendErrorMessage(id, MessageKind.CHAT, ChatError(ChatErrorCategory.NETWORK))

        store.deleteMessage(error.id)

        assertEquals(listOf("hi"), store.loadMessages(id).map { it.content })
    }
}
