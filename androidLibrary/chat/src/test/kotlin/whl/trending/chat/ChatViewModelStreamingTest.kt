package whl.trending.chat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import whl.trending.ai.chat.ChatContext
import whl.trending.chat.engine.ChatEngine
import whl.trending.chat.engine.ChatException
import whl.trending.chat.model.ChatError
import whl.trending.chat.model.ChatErrorCategory
import whl.trending.chat.model.ChatMessage
import whl.trending.chat.model.Role
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [ChatViewModel] 流式收集行为单测：增量累积、收尾标记、错误路径。 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelStreamingTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    /** 每次 emit 之间 yield，使中间态可被观察。 */
    private fun engineEmitting(vararg deltas: String) = object : ChatEngine {
        override fun send(history: List<ChatMessage>, context: ChatContext?): Flow<String> = flow {
            for (d in deltas) {
                emit(d)
                yield()
            }
        }
    }

    @Test
    fun streaming_accumulates_deltas_into_final_message() = runTest(dispatcher) {
        val vm = ChatViewModel(engine = engineEmitting("Hello", ", ", "world"))
        vm.sendText("hi")
        advanceUntilIdle()

        val last = vm.uiState.value.messages.last()
        assertEquals(Role.ASSISTANT, last.role)
        assertEquals("Hello, world", last.content)
        assertFalse(last.isStreaming, "收尾后应清除流式标记")
        assertFalse(vm.uiState.value.isSending, "收尾后 isSending 应为 false")
    }

    @Test
    fun streaming_shows_partial_prefix_before_completion() = runTest(dispatcher) {
        val vm = ChatViewModel(engine = engineEmitting("AB", "CD", "EF"))
        val states = mutableListOf<String>()

        // 收集每次 state 更新中流式消息的内容
        val collector = launch {
            vm.uiState.collect { st ->
                st.messages.lastOrNull { it.role == Role.ASSISTANT }?.let { states.add(it.content) }
            }
        }
        vm.sendText("hi")
        advanceUntilIdle()
        collector.cancel()

        // 应出现非空的中间前缀（证明是增量而非一次性整段）
        assertTrue(
            states.any { it == "AB" || it == "ABCD" },
            "应观察到中间累积前缀，实际: $states",
        )
        assertEquals("ABCDEF", states.last())
    }

    @Test
    fun failure_replaces_placeholder_with_error_message() = runTest(dispatcher) {
        val failing = object : ChatEngine {
            override fun send(history: List<ChatMessage>, context: ChatContext?): Flow<String> = flow {
                throw ChatException(ChatError(ChatErrorCategory.AUTH, httpStatus = 401))
            }
        }
        val vm = ChatViewModel(engine = failing)
        vm.sendText("hi")
        advanceUntilIdle()

        val last = vm.uiState.value.messages.last()
        assertEquals(Role.ASSISTANT, last.role)
        assertEquals(ChatErrorCategory.AUTH, last.error?.category)
        assertEquals("", last.content)
        assertFalse(last.isStreaming)
        assertFalse(vm.uiState.value.isSending)
    }
}
