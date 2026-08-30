package whl.trending.chat

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import whl.trending.chat.ChatContext
import whl.trending.chat.host.ChatAiKind
import whl.trending.chat.host.ChatAiOutcome
import whl.trending.chat.host.ChatAiEvent
import whl.trending.chat.engine.ChatEngine
import whl.trending.chat.engine.ChatException
import whl.trending.chat.engine.DetailSummaryResult
import whl.trending.chat.model.ChatError
import whl.trending.chat.model.ChatModelsResponse
import whl.trending.chat.model.ChatErrorCategory
import whl.trending.chat.model.ChatMessage
import whl.trending.chat.model.MessageKind
import whl.trending.chat.model.Role

/** ChatViewModel 流式渲染与 detail 管线路由。 */
class ChatViewModelStreamingTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val context = ChatContext(
        title = "octo/demo",
        sourceUrl = "https://github.com/octo/demo",
        source = "github",
        externalId = "octo/demo",
        readmeLength = 5000,
    )

    /** 可编程假引擎：记录调用、按脚本流式吐块或抛错 */
    private class ScriptedEngine(
        var chatChunks: List<String> = listOf("你", "好"),
        var detailChunks: List<String> = listOf("解", "读"),
        var detailCached: Boolean = false,
        var failWith: ChatError? = null,
    ) : ChatEngine {
        var chatCalls = 0
        var detailCalls = 0

        override suspend fun send(
            history: List<ChatMessage>,
            context: ChatContext?,
            onDelta: (String) -> Unit,
            search: Boolean,
            onSearch: (whl.trending.chat.model.SearchEvent) -> Unit,
        ): String {
            chatCalls++
            failWith?.let { throw ChatException(it) }
            chatChunks.forEach(onDelta)
            return chatChunks.joinToString("")
        }

        override suspend fun sendDetailSummary(
            context: ChatContext,
            onDelta: (String) -> Unit,
        ): DetailSummaryResult {
            detailCalls++
            failWith?.let { throw ChatException(it) }
            detailChunks.forEach(onDelta)
            return DetailSummaryResult(detailChunks.joinToString(""), detailCached)
        }
    }

    private fun vm(engine: ChatEngine, track: (ChatAiEvent) -> Unit = {}) =
        ChatViewModel(engine, context, loadModels = { ChatModelsResponse() }, track = track)

    @Test
    fun `发送后流式定稿：assistant 消息内容为全文，isSending 复位`() = runTest(dispatcher) {
        val viewModel = vm(ScriptedEngine())
        viewModel.sendText("hi")
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(2, state.messages.size)
        assertEquals("你好", state.messages.last().content)
        assertNull(state.messages.last().error)
        assertTrue(!state.isSending)
    }

    @Test
    fun `解读发送：插入 chip 文案 user 消息 + DETAIL_SUMMARY assistant 消息`() = runTest(dispatcher) {
        val engine = ScriptedEngine()
        val viewModel = vm(engine)
        viewModel.sendDetailSummary("一键详细解读")
        advanceUntilIdle()
        val messages = viewModel.uiState.value.messages
        assertEquals(2, messages.size)
        assertEquals(Role.USER, messages[0].role)
        assertEquals("一键详细解读", messages[0].content)
        assertEquals(MessageKind.DETAIL_SUMMARY, messages[1].kind)
        assertEquals("解读", messages[1].content)
        assertEquals(1, engine.detailCalls)
        assertEquals(0, engine.chatCalls)
    }

    @Test
    fun `失败时已渲染部分丢弃：content 清空、挂错误、kind 保留`() = runTest(dispatcher) {
        val engine = ScriptedEngine(failWith = ChatError(ChatErrorCategory.SERVER, code = "stream_interrupted"))
        val viewModel = vm(engine)
        viewModel.sendDetailSummary("一键详细解读")
        advanceUntilIdle()
        val failed = viewModel.uiState.value.messages.last()
        assertEquals("", failed.content)
        assertNotNull(failed.error)
        assertEquals(MessageKind.DETAIL_SUMMARY, failed.kind)
    }

    @Test
    fun `retry 按 kind 路由：解读失败重试走 detail 管线而非 chat`() = runTest(dispatcher) {
        val engine = ScriptedEngine(failWith = ChatError(ChatErrorCategory.SERVER, code = "stream_interrupted"))
        val viewModel = vm(engine)
        viewModel.sendDetailSummary("一键详细解读")
        advanceUntilIdle()
        engine.failWith = null // 网络恢复
        viewModel.retry(viewModel.uiState.value.messages.last())
        advanceUntilIdle()
        assertEquals(2, engine.detailCalls)
        assertEquals(0, engine.chatCalls)
        assertEquals("解读", viewModel.uiState.value.messages.last().content)
    }

    @Test
    fun `login_required 不可重试类别但享受放行例外（登录后 retry 续上）`() = runTest(dispatcher) {
        val engine = ScriptedEngine(
            failWith = ChatError(
                ChatErrorCategory.BAD_REQUEST, // 403 归类不可重试
                code = ChatError.CODE_LOGIN_REQUIRED,
                tier = ChatError.TIER_ANONYMOUS,
            ),
        )
        val viewModel = vm(engine)
        viewModel.sendDetailSummary("一键详细解读")
        advanceUntilIdle()
        engine.failWith = null // 模拟登录完成
        viewModel.retry(viewModel.uiState.value.messages.last())
        advanceUntilIdle()
        assertEquals("解读", viewModel.uiState.value.messages.last().content)
        assertEquals(2, engine.detailCalls)
    }

    @Test
    fun `埋点：requested 与 completed 成对，命中缓存与登录闸各自的 outcome`() = runTest(dispatcher) {
        val events = mutableListOf<ChatAiEvent>()
        val engine = ScriptedEngine(detailCached = true)
        val viewModel = vm(engine) { events.add(it) }
        viewModel.sendDetailSummary("一键详细解读")
        advanceUntilIdle()
        assertEquals(
            listOf(ChatAiKind.DETAIL_SUMMARY),
            events.filterIsInstance<ChatAiEvent.Requested>().map { it.kind },
        )
        assertEquals(
            listOf(ChatAiOutcome.CACHE_HIT),
            events.filterIsInstance<ChatAiEvent.Completed>().map { it.outcome },
        )

        val gated = ScriptedEngine(
            failWith = ChatError(ChatErrorCategory.BAD_REQUEST, code = ChatError.CODE_LOGIN_REQUIRED),
        )
        val events2 = mutableListOf<ChatAiEvent>()
        val vm2 = vm(gated) { events2.add(it) }
        vm2.sendDetailSummary("一键详细解读")
        advanceUntilIdle()
        assertEquals(
            listOf(ChatAiOutcome.ERROR to ChatError.CODE_LOGIN_REQUIRED),
            events2.filterIsInstance<ChatAiEvent.Completed>().map { it.outcome to it.reason },
        )
    }
}
