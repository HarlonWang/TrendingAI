package whl.trending.chat

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import whl.trending.ai.chat.ChatContext
import whl.trending.chat.engine.ChatEngine
import whl.trending.chat.engine.ChatException
import whl.trending.chat.engine.DetailSummaryResult
import whl.trending.chat.db.ChatDatabase
import whl.trending.chat.model.ChatError
import whl.trending.chat.model.ChatErrorCategory
import whl.trending.chat.model.ChatMessage
import whl.trending.chat.model.Role
import whl.trending.chat.store.ChatStore
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * VM × 持久化联动：懒建落库 / 终局一次写 / 入口恢复 / 会话切换 / 后台流完成落库。
 * 真 ChatStore + 内存 Room（Robolectric，sdk 钉 35 同前）；引擎为可编程假实现。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChatViewModelPersistenceTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: ChatDatabase
    private lateinit var imagesDir: File
    private lateinit var store: ChatStore

    private val repoContext = ChatContext(
        title = "octo/demo", source = "github", externalId = "octo/demo", readmeLength = 5000,
    )

    /** 可挂起的假引擎：release 前流不结束，模拟「切换会话时流仍在进行」 */
    private class GatedEngine(
        var reply: String = "回答",
        var failWith: ChatError? = null,
        var gate: kotlinx.coroutines.CompletableDeferred<Unit>? = null,
    ) : ChatEngine {
        override suspend fun send(
            history: List<ChatMessage>,
            context: ChatContext?,
            onDelta: (String) -> Unit,
        ): String {
            failWith?.let { throw ChatException(it) }
            onDelta(reply)
            gate?.await()
            return reply
        }

        override suspend fun sendDetailSummary(context: ChatContext, onDelta: (String) -> Unit): DetailSummaryResult {
            onDelta(reply)
            return DetailSummaryResult(reply, cached = false)
        }
    }

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), ChatDatabase::class.java,
        )
            // 直通执行器：Room suspend DAO 默认走自有线程池，advanceUntilIdle 等不到真实线程
            // 上的恢复点会早返回——直通后全部调度都留在测试调度器里
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .allowMainThreadQueries()
            .build()
        imagesDir = File.createTempFile("imgs", null).apply { delete(); mkdirs() }
        store = ChatStore(db, imagesDir, clock = { 1000L })
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        db.close()
        imagesDir.deleteRecursively()
    }

    private fun vm(engine: ChatEngine, context: ChatContext? = null) =
        ChatViewModel(engine, context, store = store, loadModels = { emptyList() }, track = { _, _ -> }, selectedModelId = { "gpt-5.5" })

    @Test
    fun `首条发送懒建线程，user 与 assistant 终局各落一行，model 记录在案`() = runTest(dispatcher) {
        val v = vm(GatedEngine(reply = "你好"), repoContext)
        advanceUntilIdle()
        v.updateInput("介绍一下")
        v.send()
        advanceUntilIdle()

        val threads = store.threads().first()
        assertEquals(1, threads.size)
        assertEquals("octo/demo", threads[0].title)
        val rows = db.messageDao().messagesFor(threads[0].id)
        assertEquals(listOf("user", "assistant"), rows.map { it.role })
        assertEquals("你好", rows[1].content)
        assertEquals("gpt-5.5", rows[1].model)
    }

    @Test
    fun `失败回复不落库，user 消息保留`() = runTest(dispatcher) {
        val v = vm(GatedEngine(failWith = ChatError(ChatErrorCategory.NETWORK)))
        advanceUntilIdle()
        v.updateInput("hi")
        v.send()
        advanceUntilIdle()

        val threads = store.threads().first()
        val rows = db.messageDao().messagesFor(threads[0].id)
        assertEquals(listOf("user"), rows.map { it.role })
        // UI 上错误条可见可重试
        assertNotNull(v.uiState.value.messages.last().error)
    }

    @Test
    fun `入口恢复：同 repo 再次构造 VM 载入历史消息`() = runTest(dispatcher) {
        val first = vm(GatedEngine(reply = "答一"), repoContext)
        advanceUntilIdle()
        first.updateInput("问一")
        first.send()
        advanceUntilIdle()

        val second = vm(GatedEngine(), repoContext)
        advanceUntilIdle()
        val restored = second.uiState.value.messages
        assertEquals(listOf("问一", "答一"), restored.map { it.content })
        assertEquals(listOf(Role.USER, Role.ASSISTANT), restored.map { it.role })
    }

    @Test
    fun `startNewThread 后发送总是新建线程，不复用入口最近会话`() = runTest(dispatcher) {
        val v = vm(GatedEngine(reply = "答"), repoContext)
        advanceUntilIdle()
        v.updateInput("第一线")
        v.send()
        advanceUntilIdle()

        v.startNewThread()
        assertTrue(v.uiState.value.messages.isEmpty())
        v.updateInput("第二线")
        v.send()
        advanceUntilIdle()

        assertEquals(2, store.threads().first().size)
    }

    @Test
    fun `switchThread 载入目标线程消息与 id 续位`() = runTest(dispatcher) {
        val v = vm(GatedEngine(reply = "答A"), repoContext)
        advanceUntilIdle()
        v.updateInput("问A")
        v.send()
        advanceUntilIdle()
        val threadA = store.threads().first()[0].id

        v.startNewThread()
        v.updateInput("问B")
        v.send()
        advanceUntilIdle()

        v.switchThread(threadA)
        advanceUntilIdle()
        assertEquals(listOf("问A", "答A"), v.uiState.value.messages.map { it.content })
    }

    @Test
    fun `切换会话时后台流继续完成并落库，切回可见全文`() = runTest(dispatcher) {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val v = vm(GatedEngine(reply = "慢答案", gate = gate), repoContext)
        advanceUntilIdle()
        v.updateInput("慢问题")
        v.send()
        advanceUntilIdle() // 流启动，卡在 gate
        val threadA = store.threads().first()[0].id
        assertTrue(v.uiState.value.isSending)

        v.startNewThread() // 切走：A 的流在后台继续
        assertEquals(false, v.uiState.value.isSending)

        gate.complete(Unit)
        advanceUntilIdle()

        // A 已终局落库
        val rows = db.messageDao().messagesFor(threadA)
        assertEquals(listOf("user", "assistant"), rows.map { it.role })
        assertEquals("慢答案", rows[1].content)

        // 切回 A 看到全文
        v.switchThread(threadA)
        advanceUntilIdle()
        assertEquals("慢答案", v.uiState.value.messages.last().content)
    }

    @Test
    fun `deleteThread 删除当前会话后回到空态，行与文件同清`() = runTest(dispatcher) {
        val v = vm(GatedEngine(reply = "答"), repoContext)
        advanceUntilIdle()
        v.updateInput("问")
        v.send()
        advanceUntilIdle()
        val threadId = store.threads().first()[0].id

        v.deleteThread(threadId)
        advanceUntilIdle()

        assertTrue(store.threads().first().isEmpty())
        assertTrue(v.uiState.value.messages.isEmpty())
    }
}
