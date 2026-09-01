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
import whl.trending.chat.db.ChatDatabase
import whl.trending.chat.model.ChatError
import whl.trending.chat.model.ChatErrorCategory
import whl.trending.chat.model.Role
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Room 持久化层行为：建线与标题策略 / context 往返 / 图片迁移 filesDir /
 * 错误行落库往返 / 删除先删文件。
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
    fun `建线：标题取首条消息`() = runTest {
        val id = store.createThread(firstMessageText = "介绍一下")
        assertEquals("介绍一下", db.threadDao().getById(id)!!.title)
    }

    @Test
    fun `通用入口标题取首条消息，超长截断`() = runTest {
        val id = store.createThread(firstMessageText = "这是一条非常非常非常非常非常非常长的首条消息内容")
        val title = db.threadDao().getById(id)!!.title
        assertTrue(title.length <= ChatStore.MAX_TITLE_LENGTH)
        assertTrue(title.startsWith("这是一条"))
    }

    @Test
    fun `纯图消息无文本 → 默认标题`() = runTest {
        val id = store.createThread(firstMessageText = "")
        assertEquals(ChatStore.DEFAULT_TITLE, db.threadDao().getById(id)!!.title)
    }

    // 消息落库

    @Test
    fun `appendUserMessage 把图片从 cache 拷入 filesDir 并回写新路径，id 即行 id`() = runTest {
        val cachePath = cacheImage("a.jpg")
        val id = store.createThread(firstMessageText = "看图")
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
    fun `loadMessages 完整还原 model 层字段`() = runTest {
        val id = store.createThread(firstMessageText = "提问")
        store.appendUserMessage(id, "提问")
        store.appendAssistantMessage(id, "回答内容", model = "gpt-5.5")

        val loaded = store.loadMessages(id)
        assertEquals(2, loaded.size)
        assertEquals(Role.ASSISTANT, loaded[1].role)
        assertEquals("回答内容", loaded[1].content)
        assertEquals("gpt-5.5", loaded[1].model)
        assertTrue(loaded[1].id > loaded[0].id)
    }

    @Test
    fun `存量残留的空 assistant 行（已下线的 research 占位）不外发`() = runTest {
        val id = store.createThread(firstMessageText = "老会话")
        store.appendUserMessage(id, "老提问")
        db.messageDao().insert(
            whl.trending.chat.db.MessageEntity(
                threadId = id, role = "assistant", content = "",
                imagesJson = null, model = null, segmentsJson = null, createdAt = now,
            ),
        )
        assertEquals(listOf("老提问"), store.loadMessages(id).map { it.content })
    }

    @Test
    fun `错误行落库往返：分类、机器码、tier、authDegraded 全还原`() = runTest {
        val id = store.createThread(firstMessageText = "hi")
        store.appendUserMessage(id, "hi")
        val error = ChatError(
            ChatErrorCategory.QUOTA,
            code = ChatError.CODE_QUOTA_DEVICE,
            httpStatus = 429,
            detail = "quota exceeded",
            tier = ChatError.TIER_ANONYMOUS,
            authDegraded = true,
        )
        store.appendErrorMessage(id, error)

        val loaded = store.loadMessages(id).last()
        assertEquals("", loaded.content)
        assertEquals(error, loaded.error)
    }

    // 删除

    @Test
    fun `deleteThread 先删图片文件再删行`() = runTest {
        val cachePath = cacheImage("b.jpg")
        val id = store.createThread(firstMessageText = "看图")
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
        val id = store.createThread(firstMessageText = "看图")
        val persisted = store.appendUserMessage(id, "看图", images = listOf(outside))
        assertEquals(outside, persisted.images[0])

        File(outside).writeBytes(byteArrayOf(1)) // 文件随后出现在 store 之外

        store.deleteThread(id)

        assertTrue(File(outside).exists()) // 只删 imagesDir 名下的文件
        assertNull(db.threadDao().getById(id))
    }

    @Test
    fun `deleteMessage 只删目标行`() = runTest {
        val id = store.createThread(firstMessageText = "hi")
        store.appendUserMessage(id, "hi")
        val error = store.appendErrorMessage(id, ChatError(ChatErrorCategory.NETWORK))

        store.deleteMessage(error.id)

        assertEquals(listOf("hi"), store.loadMessages(id).map { it.content })
    }
}
