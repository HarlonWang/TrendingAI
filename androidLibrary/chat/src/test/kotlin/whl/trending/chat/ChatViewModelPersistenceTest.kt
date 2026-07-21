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
            search: Boolean,
            onSearch: (whl.trending.chat.model.SearchEvent) -> Unit,
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

    /** 构造并执行入口进入（Screen 的 enterEntry 时序在测试里显式驱动） */
    private fun vm(engine: ChatEngine, context: ChatContext? = null) =
        ChatViewModel(engine, context, store = store, loadModels = { emptyList() }, track = { _, _ -> }, selectedModelId = { "gpt-5.5" })
            .also { it.enterEntry(context) }

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

    // ---- P2 web search ----

    /** 带搜索事件的假引擎 */
    private class SearchEngine(var reply: String = "答案") : ChatEngine {
        var lastSearchFlag = false
        override suspend fun send(
            history: List<ChatMessage>,
            context: ChatContext?,
            onDelta: (String) -> Unit,
            search: Boolean,
            onSearch: (whl.trending.chat.model.SearchEvent) -> Unit,
        ): String {
            lastSearchFlag = search
            if (search) {
                onSearch(whl.trending.chat.model.SearchEvent.Started)
                onSearch(whl.trending.chat.model.SearchEvent.Done("q"))
                onSearch(whl.trending.chat.model.SearchEvent.Source("Kotlin", "https://kotlinlang.org"))
            }
            onDelta(reply)
            return reply
        }
        override suspend fun sendDetailSummary(context: ChatContext, onDelta: (String) -> Unit) =
            DetailSummaryResult(reply, cached = false)
    }

    @Test
    fun `搜索模式：引擎收到 search=true，来源落到消息并随消息持久化`() = runTest(dispatcher) {
        val engine = SearchEngine()
        val v = vm(engine)
        advanceUntilIdle()
        v.toggleWebSearch()
        v.updateInput("查一下")
        v.send()
        advanceUntilIdle()

        assertTrue(engine.lastSearchFlag)
        val msg = v.uiState.value.messages.last()
        assertEquals(listOf("https://kotlinlang.org"), msg.sources.map { it.url })
        assertEquals(false, msg.searching)

        // 持久化往返：重新加载后 sources 还在（segmentsJson v1 信封）
        val threadId = store.threads().first()[0].id
        val loaded = store.loadMessages(threadId)
        assertEquals(listOf("https://kotlinlang.org"), loaded.last().sources.map { it.url })
    }

    @Test
    fun `未开搜索模式：引擎收到 search=false`() = runTest(dispatcher) {
        val engine = SearchEngine()
        val v = vm(engine)
        advanceUntilIdle()
        v.updateInput("普通问题")
        v.send()
        advanceUntilIdle()
        assertEquals(false, engine.lastSearchFlag)
    }

    @Test
    fun `toggle 两次回到 Normal（单选互斥范式）`() = runTest(dispatcher) {
        val v = vm(SearchEngine())
        v.toggleWebSearch()
        assertEquals(whl.trending.chat.model.ChatMode.WebSearch, v.chatMode.value)
        v.toggleWebSearch()
        assertEquals(whl.trending.chat.model.ChatMode.Normal, v.chatMode.value)
    }


    // ---- P3 Deep Research ----

    /** research 假引擎：脚本化状态序列 */
    private class ResearchEngine(
        var statuses: MutableList<whl.trending.chat.model.ResearchRun> = mutableListOf(),
        var failCreate: ChatError? = null,
    ) : ChatEngine {
        var created = 0
        override suspend fun send(
            history: List<ChatMessage>, context: ChatContext?, onDelta: (String) -> Unit,
            search: Boolean, onSearch: (whl.trending.chat.model.SearchEvent) -> Unit,
        ): String = "n/a"
        override suspend fun sendDetailSummary(context: ChatContext, onDelta: (String) -> Unit) =
            DetailSummaryResult("n/a", cached = false)
        override suspend fun createResearch(topic: String): String {
            failCreate?.let { throw ChatException(it) }
            created++
            return "run-77"
        }
        override suspend fun pollResearch(id: String): whl.trending.chat.model.ResearchRun =
            if (statuses.size > 1) statuses.removeAt(0) else statuses[0]
    }

    @Test
    fun `research 提交→占位落库→轮询完成→报告落库`() = runTest(dispatcher) {
        val engine = ResearchEngine(mutableListOf(
            whl.trending.chat.model.ResearchRun("run-77", "running", null, null),
            whl.trending.chat.model.ResearchRun("run-77", "completed", "# 研究报告", null),
        ))
        val v = vm(engine)
        advanceUntilIdle()
        v.toggleDeepResearch()
        v.updateInput("研究一下 KMP")
        v.send()
        advanceUntilIdle()

        val msg = v.uiState.value.messages.last()
        assertEquals("# 研究报告", msg.content)
        assertEquals(false, msg.searching)

        val threadId = store.threads().first()[0].id
        val rows = db.messageDao().messagesFor(threadId)
        assertEquals("DEEP_RESEARCH", rows.last().kind)
        assertEquals("# 研究报告", rows.last().content)
        assertTrue(rows.last().segmentsJson!!.contains("run-77"))
    }

    @Test
    fun `research 失败→删占位行→错误条可见`() = runTest(dispatcher) {
        val engine = ResearchEngine(mutableListOf(
            whl.trending.chat.model.ResearchRun("run-77", "failed", null, "failed"),
        ))
        val v = vm(engine)
        advanceUntilIdle()
        v.toggleDeepResearch()
        v.updateInput("x")
        v.send()
        advanceUntilIdle()

        assertNotNull(v.uiState.value.messages.last().error)
        val threadId = store.threads().first()[0].id
        val rows = db.messageDao().messagesFor(threadId)
        // 占位行已删，只剩 user 消息
        assertEquals(listOf("user"), rows.map { it.role })
    }

    @Test
    fun `重开会话发现未完成占位→恢复轮询直至完成`() = runTest(dispatcher) {
        val engine = ResearchEngine(mutableListOf(
            whl.trending.chat.model.ResearchRun("run-77", "running", null, null),
        ))
        val v = vm(engine)
        advanceUntilIdle()
        v.toggleDeepResearch()
        v.updateInput("长任务")
        v.send()
        // 只让创建完成、不推进轮询到终局：先切走再回来模拟重开
        advanceUntilIdle()

        // 新 VM（模拟进程重启）：占位行在库里 → enterEntry 恢复轮询
        val engine2 = ResearchEngine(mutableListOf(
            whl.trending.chat.model.ResearchRun("run-77", "completed", "迟到的报告", null),
        ))
        val v2 = vm(engine2)
        advanceUntilIdle()

        assertEquals("迟到的报告", v2.uiState.value.messages.last().content)
    }

}
