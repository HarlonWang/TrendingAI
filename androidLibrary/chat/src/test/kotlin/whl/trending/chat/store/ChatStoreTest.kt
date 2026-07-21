package whl.trending.chat.store

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import whl.trending.ai.chat.ChatContext
import whl.trending.chat.db.ChatDatabase
import whl.trending.chat.model.ChatMessage
import whl.trending.chat.model.MessageKind
import whl.trending.chat.model.Role
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 持久化层行为测试：懒建会话 / 入口恢复复用 / context 往返 / 标题策略 /
 * 图片迁移 filesDir / 终局一次写（空回复不落）/ 删除先删文件。
 * sdk 钉 35：同 ChatDatabaseTest（Robolectric 4.16.x + 测试 JVM 17 的上限）
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChatStoreTest {

    private lateinit var db: ChatDatabase
    private lateinit var imagesDir: File
    private lateinit var cacheDir: File
    private var now = 1000L
    private lateinit var store: ChatStore

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
        store = ChatStore(db, imagesDir, clock = { now })
    }

    @After
    fun teardown() {
        db.close()
        imagesDir.deleteRecursively()
        cacheDir.deleteRecursively()
    }

    private fun userMsg(text: String, images: List<String> = emptyList()) =
        ChatMessage(id = 0, role = Role.USER, content = text, images = images)

    private fun cacheImage(name: String): String =
        File(cacheDir, name).apply { writeBytes(byteArrayOf(1, 2, 3)) }.absolutePath

    // ---- 懒建与入口恢复 ----

    @Test
    fun `repo 入口懒建：标题取 context 标题，entryKey 为 repo 前缀`() = runTest {
        val id = store.ensureThread(repoContext, firstMessageText = "介绍一下")
        val thread = db.threadDao().getById(id)!!
        assertEquals("octo/demo", thread.title)
        assertEquals("repo:octo/demo", thread.entryKey)
    }

    @Test
    fun `同入口再次 ensure 复用最近会话，不新建`() = runTest {
        val first = store.ensureThread(repoContext, firstMessageText = "hi")
        val second = store.ensureThread(repoContext, firstMessageText = "again")
        assertEquals(first, second)
    }

    @Test
    fun `resolveLatestThread 无历史返回 null（进入即空态，不落库）`() = runTest {
        assertNull(store.resolveLatestThread(null))
        assertEquals(0, db.threadDao().observeAll().first().size)
    }

    // ---- context 往返 ----

    @Test
    fun `contextJson 往返：恢复的会话还原 ChatContext 关键字段`() = runTest {
        val id = store.ensureThread(repoContext, firstMessageText = "hi")
        val restored = store.contextOf(id)!!
        assertEquals("octo/demo", restored.title)
        assertEquals("github", restored.source)
        assertEquals("octo/demo", restored.externalId)
        assertEquals(2000, restored.readmeLength)
        // autoDetailSummary 是一次性触发标记，不持久化
        assertFalse(restored.autoDetailSummary)
    }

    // ---- 标题策略 ----

    @Test
    fun `通用入口标题取首条消息，超长截断`() = runTest {
        val id = store.ensureThread(null, firstMessageText = "这是一条非常非常非常非常非常非常长的首条消息内容")
        val title = db.threadDao().getById(id)!!.title
        assertTrue(title.length <= ChatStore.MAX_TITLE_LENGTH)
        assertTrue(title.startsWith("这是一条"))
    }

    @Test
    fun `纯图消息无文本 → 默认标题`() = runTest {
        val id = store.ensureThread(null, firstMessageText = "")
        assertEquals(ChatStore.DEFAULT_TITLE, db.threadDao().getById(id)!!.title)
    }

    // ---- 消息落库 ----

    @Test
    fun `persistUserMessage 把图片从 cache 拷入 filesDir 并回写新路径`() = runTest {
        val cachePath = cacheImage("a.jpg")
        val id = store.ensureThread(null, firstMessageText = "看图")
        val persisted = store.persistUserMessage(id, userMsg("看图", images = listOf(cachePath)))

        assertEquals(1, persisted.images.size)
        val newPath = persisted.images[0]
        assertTrue(newPath.startsWith(imagesDir.absolutePath))
        assertTrue(File(newPath).exists())

        val rows = db.messageDao().messagesFor(id)
        assertEquals(1, rows.size)
        assertTrue(rows[0].imagesJson!!.contains(newPath))
    }

    @Test
    fun `persistAssistantMessage 成功终局落一次，空内容不落`() = runTest {
        val id = store.ensureThread(null, firstMessageText = "hi")
        store.persistAssistantMessage(id, content = "回答", kind = MessageKind.CHAT, model = "gpt-5.4")
        store.persistAssistantMessage(id, content = "", kind = MessageKind.CHAT, model = null)

        val rows = db.messageDao().messagesFor(id)
        assertEquals(1, rows.size)
        assertEquals("assistant", rows[0].role)
        assertEquals("gpt-5.4", rows[0].model)
    }

    @Test
    fun `loadMessages 完整还原 model 层字段（kind与图片）`() = runTest {
        val id = store.ensureThread(repoContext, firstMessageText = "解读")
        store.persistUserMessage(
            id,
            ChatMessage(0, Role.USER, "解读", kind = MessageKind.DETAIL_SUMMARY),
        )
        store.persistAssistantMessage(id, "解读内容", MessageKind.DETAIL_SUMMARY, model = null)

        val loaded = store.loadMessages(id)
        assertEquals(2, loaded.size)
        assertEquals(MessageKind.DETAIL_SUMMARY, loaded[0].kind)
        assertEquals(Role.ASSISTANT, loaded[1].role)
        assertEquals("解读内容", loaded[1].content)
        // id 单调递增（VM 的 idSeq 从 max(id) 续）
        assertTrue(loaded[1].id > loaded[0].id)
    }

    // ---- 删除 ----

    @Test
    fun `deleteThread 先删图片文件再删行`() = runTest {
        val cachePath = cacheImage("b.jpg")
        val id = store.ensureThread(null, firstMessageText = "看图")
        val persisted = store.persistUserMessage(id, userMsg("看图", images = listOf(cachePath)))
        val storedFile = File(persisted.images[0])
        assertTrue(storedFile.exists())

        store.deleteThread(id)

        assertFalse(storedFile.exists())
        assertNull(db.threadDao().getById(id))
        assertTrue(db.messageDao().messagesFor(id).isEmpty())
    }
}
