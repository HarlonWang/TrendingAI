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
import kotlin.test.assertNull
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
        ChatViewModel(engine, context, store = store, loadModels = { whl.trending.ai.data.model.ChatModelsResponse() }, track = { _, _ -> }, selectedModelId = { "gpt-5.5" })
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

    /** research 假引擎：脚本化状态序列 + 可编程失败 + history 捕获 */
    private class ResearchEngine(
        var statuses: MutableList<whl.trending.chat.model.ResearchRun> = mutableListOf(),
        var failCreate: ChatError? = null,
        var failPoll: ChatError? = null,
    ) : ChatEngine {
        var created = 0
        var polled = 0
        var lastHistory: List<ChatMessage>? = null
        override suspend fun send(
            history: List<ChatMessage>, context: ChatContext?, onDelta: (String) -> Unit,
            search: Boolean, onSearch: (whl.trending.chat.model.SearchEvent) -> Unit,
        ): String {
            lastHistory = history
            return "n/a"
        }
        override suspend fun sendDetailSummary(context: ChatContext, onDelta: (String) -> Unit) =
            DetailSummaryResult("n/a", cached = false)
        override suspend fun createResearch(topic: String): String {
            failCreate?.let { throw ChatException(it) }
            created++
            return "run-77"
        }
        override suspend fun pollResearch(id: String): whl.trending.chat.model.ResearchRun {
            failPoll?.let { throw ChatException(it) }
            polled++
            return if (statuses.size > 1) statuses.removeAt(0) else statuses[0]
        }
    }

    @Test
    fun `research 提交→占位落库→轮询完成→报告落库`() = runTest(dispatcher) {
        val engine = ResearchEngine(mutableListOf(
            whl.trending.chat.model.ResearchRun("run-77", "running", null, null),
            whl.trending.chat.model.ResearchRun("run-77", "completed", "# 研究报告", null, "gpt-5.5"),
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
        assertEquals("gpt-5.5", msg.model) // 生成模型随报告透传

        val threadId = store.threads().first()[0].id
        val rows = db.messageDao().messagesFor(threadId)
        assertEquals("DEEP_RESEARCH", rows.last().kind)
        assertEquals("# 研究报告", rows.last().content)
        assertEquals("gpt-5.5", rows.last().model) // 模型留痕随报告落库
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

        val threadId = store.threads().first()[0].id

        // 新 VM（模拟进程重启）：通用入口进来是**新会话**，那条挂着任务的会话并不会被打开——
        // 恢复轮询因此不能挂在会话恢复上，否则这 10 credits 的任务永远没人接。
        // 任务照常跑完落库，用户从抽屉切过去就能看到完整报告。
        val engine2 = ResearchEngine(mutableListOf(
            whl.trending.chat.model.ResearchRun("run-77", "completed", "迟到的报告", null),
        ))
        val v2 = vm(engine2)
        advanceUntilIdle()

        assertTrue(v2.uiState.value.messages.isEmpty())
        assertEquals("迟到的报告", db.messageDao().messagesFor(threadId).last().content)
    }

    @Test
    fun `进程内再次进入通用入口：续接当前会话，不重置`() = runTest(dispatcher) {
        val v = vm(GatedEngine(reply = "答"))
        advanceUntilIdle()
        v.updateInput("问")
        v.send()
        advanceUntilIdle()
        val threadId = v.currentThreadId.value
        assertNotNull(threadId)

        // 模拟「返回首页再进 chat」：VM 挂在 Activity 作用域仍存活，而 Screen 侧 enteredKey
        // 随新 NavEntry 重置 → enterEntry 会再被调一次。此时应保持现状，不另开会话
        v.enterEntry(null)
        advanceUntilIdle()

        assertEquals(threadId, v.currentThreadId.value)
        assertEquals(listOf("问", "答"), v.uiState.value.messages.map { it.content })
        assertEquals(1, store.threads().first().size)
    }

    @Test
    fun `停在条目会话时走通用入口：开新会话，不把 repo 会话当通用会话续上`() = runTest(dispatcher) {
        val v = vm(GatedEngine(reply = "答"), repoContext)
        advanceUntilIdle()
        v.updateInput("问")
        v.send()
        advanceUntilIdle()

        v.enterEntry(null)
        advanceUntilIdle()

        assertNull(v.currentThreadId.value)
        assertTrue(v.uiState.value.messages.isEmpty())
    }

    @Test
    fun `通用入口进入即新会话，不续上次；历史仍在抽屉可切回`() = runTest(dispatcher) {
        val first = vm(GatedEngine(reply = "答一"))
        advanceUntilIdle()
        first.updateInput("问一")
        first.send()
        advanceUntilIdle()
        val threadId = store.threads().first()[0].id

        val second = vm(GatedEngine())
        advanceUntilIdle()
        assertTrue(second.uiState.value.messages.isEmpty())
        assertNull(second.currentThreadId.value)

        // 历史没丢：抽屉里还在，切回去内容完整
        assertEquals(1, second.threads.value.size)
        second.switchThread(threadId)
        advanceUntilIdle()
        assertEquals(listOf("问一", "答一"), second.uiState.value.messages.map { it.content })
    }

    @Test
    fun `research 空报告视同失败：删占位行、错误可重试、不留恢复哨兵`() = runTest(dispatcher) {
        val engine = ResearchEngine(mutableListOf(
            whl.trending.chat.model.ResearchRun("run-77", "completed", "", null),
        ))
        val v = vm(engine)
        advanceUntilIdle()
        v.toggleDeepResearch()
        v.updateInput("空手而归")
        v.send()
        advanceUntilIdle()

        val last = v.uiState.value.messages.last()
        assertNotNull(last.error)
        assertEquals(null, last.researchRunId)
        // 占位行已删：库里只剩 user，重开会话不会再触发恢复轮询
        val threadId = store.threads().first()[0].id
        assertEquals(listOf("user"), db.messageDao().messagesFor(threadId).map { it.role })
    }

    @Test
    fun `error 条占过内存 id 后，research 占位 id 不与已有消息重号`() = runTest(dispatcher) {
        // 第一次提交失败：error 条占用内存 id 但不落库 → Room 自增从此落后于内存序列
        val engine = ResearchEngine(
            statuses = mutableListOf(whl.trending.chat.model.ResearchRun("run-77", "completed", "# 报告", null)),
            failCreate = ChatError(ChatErrorCategory.SERVER),
        )
        val v = vm(engine)
        advanceUntilIdle()
        v.toggleDeepResearch()
        v.updateInput("第一问")
        v.send()
        advanceUntilIdle()

        engine.failCreate = null
        v.updateInput("第二问")
        v.send()
        advanceUntilIdle()

        val messages = v.uiState.value.messages
        // id 全局唯一（重号会让 LazyColumn 的 key 崩溃、报告覆盖用户气泡）
        assertEquals(messages.size, messages.map { it.id }.distinct().size)
        // 用户自己的提问未被报告内容覆盖
        assertEquals(listOf("第一问", "第二问"), messages.filter { it.role == Role.USER }.map { it.content })
        assertEquals("# 报告", messages.last().content)
    }

    @Test
    fun `retry research 提交失败条→重新提交而非撞流式管线`() = runTest(dispatcher) {
        val engine = ResearchEngine(
            statuses = mutableListOf(whl.trending.chat.model.ResearchRun("run-77", "completed", "# 报告", null)),
            failCreate = ChatError(ChatErrorCategory.SERVER),
        )
        val v = vm(engine)
        advanceUntilIdle()
        v.toggleDeepResearch()
        v.updateInput("研究题")
        v.send()
        advanceUntilIdle()
        val errorRow = v.uiState.value.messages.last()
        assertNotNull(errorRow.error)

        engine.failCreate = null
        v.retry(errorRow)
        advanceUntilIdle()

        assertEquals(1, engine.created)
        assertEquals("# 报告", v.uiState.value.messages.last().content)
        // user 提问只有一条，未被重试复制
        val threadId = store.threads().first()[0].id
        assertEquals(listOf("user", "assistant"), db.messageDao().messagesFor(threadId).map { it.role })
    }

    @Test
    fun `retry research 轮询永久错误条→恢复轮询同一任务，不重复建任务`() = runTest(dispatcher) {
        val engine = ResearchEngine(
            statuses = mutableListOf(whl.trending.chat.model.ResearchRun("run-77", "completed", "# 报告", null)),
            failPoll = ChatError(ChatErrorCategory.QUOTA, code = ChatError.CODE_QUOTA_DEVICE),
        )
        val v = vm(engine)
        advanceUntilIdle()
        v.toggleDeepResearch()
        v.updateInput("配额题")
        v.send()
        advanceUntilIdle()
        val errorRow = v.uiState.value.messages.last()
        assertNotNull(errorRow.error)
        assertEquals("run-77", errorRow.researchRunId) // runId 保留是恢复的前提

        engine.failPoll = null
        v.retry(errorRow)
        advanceUntilIdle()

        assertEquals(1, engine.created) // 没有重复建任务（重复扣费）
        assertEquals("# 报告", v.uiState.value.messages.last().content)
    }

    @Test
    fun `不支持 research 的引擎（Demo 默认实现）：不崩溃、呈现错误条`() = runTest(dispatcher) {
        // GatedEngine 未覆写 createResearch → 默认实现抛 UnsupportedOperationException
        val v = vm(GatedEngine())
        advanceUntilIdle()
        v.toggleDeepResearch()
        v.updateInput("不支持")
        v.send()
        advanceUntilIdle()

        assertNotNull(v.uiState.value.messages.last().error)
        assertEquals(false, v.uiState.value.isSending)
    }

    @Test
    fun `空 assistant 行（research 错误条）不进后续 chat history`() = runTest(dispatcher) {
        val engine = ResearchEngine(failCreate = ChatError(ChatErrorCategory.SERVER))
        val v = vm(engine)
        advanceUntilIdle()
        v.toggleDeepResearch()
        v.updateInput("失败的研究")
        v.send()
        advanceUntilIdle()

        v.toggleDeepResearch() // 回 Normal
        v.updateInput("普通问题")
        v.send()
        advanceUntilIdle()

        val history = engine.lastHistory
        assertNotNull(history)
        assertTrue(history.none { it.role == Role.ASSISTANT && it.content.isBlank() })
    }

    @Test
    fun `deleteThread 取消 research 轮询任务`() = runTest(dispatcher) {
        val engine = ResearchEngine(mutableListOf(
            whl.trending.chat.model.ResearchRun("run-77", "running", null, null),
        ))
        val v = vm(engine)
        advanceUntilIdle()
        v.toggleDeepResearch()
        v.updateInput("长任务")
        v.send()
        testScheduler.runCurrent()
        testScheduler.advanceTimeBy(20_000) // 快轮阶段推进两轮
        val polledBefore = engine.polled
        assertTrue(polledBefore > 0)

        val threadId = store.threads().first()[0].id
        v.deleteThread(threadId)
        advanceUntilIdle()

        assertEquals(polledBefore, engine.polled) // 轮询已随线程删除而取消
    }

}
