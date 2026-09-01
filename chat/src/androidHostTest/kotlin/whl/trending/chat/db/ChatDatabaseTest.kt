package whl.trending.chat.db

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
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * schema 特征测试：钉住表结构契约（research 恢复查询 / 排序 / 级联删除 / 字段往返）。
 * Robolectric 内存库，行为即真实 SQLite。
 * sdk 钉 35：Robolectric 4.16.x 不支持 37，SDK 36 沙箱又要求 Java 21（测试 JVM 为 17）
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChatDatabaseTest {

    private lateinit var db: ChatDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ChatDatabase::class.java,
        ).build()
    }

    @After
    fun teardown() = db.close()

    private fun thread(updatedAt: Long = 0L, title: String = "t") =
        ThreadEntity(title = title, createdAt = updatedAt, updatedAt = updatedAt)

    private fun message(threadId: Long, content: String = "hi", role: String = "user") =
        MessageEntity(
            threadId = threadId, role = role, content = content,
            imagesJson = null, kind = "CHAT", model = null, segmentsJson = null, createdAt = 0L,
        )

    @Test
    fun `pendingResearchRows 只取空内容的 research 行`() = runTest {
        val t = db.threadDao().insert(thread())
        db.messageDao().insert(message(t)) // user 行不入
        val pending = db.messageDao().insert(
            message(t, role = "assistant", content = "").copy(kind = "DEEP_RESEARCH"),
        )
        db.messageDao().insert(
            message(t, role = "assistant", content = "# 报告").copy(kind = "DEEP_RESEARCH"),
        )

        assertEquals(listOf(pending), db.messageDao().pendingResearchRows("DEEP_RESEARCH").map { it.id })
    }

    @Test
    fun `列表按 updatedAt 倒序，touch 冒泡到顶部`() = runTest {
        val a = db.threadDao().insert(thread(title = "a", updatedAt = 100))
        val b = db.threadDao().insert(thread(title = "b", updatedAt = 200))

        assertEquals(listOf(b, a), db.threadDao().observeAll().first().map { it.id })

        db.threadDao().touch(a, now = 300)
        assertEquals(listOf(a, b), db.threadDao().observeAll().first().map { it.id })
    }

    @Test
    fun `删线程级联删消息`() = runTest {
        val t = db.threadDao().insert(thread())
        db.messageDao().insert(message(t))
        db.messageDao().insert(message(t, role = "assistant", content = "ok"))

        db.threadDao().delete(t)

        assertNull(db.threadDao().getById(t))
        assertTrue(db.messageDao().messagesFor(t).isEmpty())
    }

    @Test
    fun `消息字段完整往返，按插入顺序读出`() = runTest {
        val t = db.threadDao().insert(thread())
        db.messageDao().insert(
            message(t).copy(imagesJson = """["/data/img1.jpg"]""", kind = "DETAIL_SUMMARY", model = "gpt-5.5"),
        )
        db.messageDao().insert(message(t, role = "assistant", content = "答案"))

        val loaded = db.messageDao().messagesFor(t)
        assertEquals(2, loaded.size)
        assertEquals("""["/data/img1.jpg"]""", loaded[0].imagesJson)
        assertEquals("DETAIL_SUMMARY", loaded[0].kind)
        assertEquals("gpt-5.5", loaded[0].model)
        assertNull(loaded[0].segmentsJson)
        assertEquals("答案", loaded[1].content)
    }

    @Test
    fun `imagesJsonFor 只取带图消息的路径列`() = runTest {
        val t = db.threadDao().insert(thread())
        db.messageDao().insert(message(t))
        db.messageDao().insert(message(t).copy(imagesJson = """["/a.jpg","/b.jpg"]"""))

        val jsons = db.messageDao().imagesJsonFor(t)
        assertEquals(listOf("""["/a.jpg","/b.jpg"]"""), jsons)
    }
}
