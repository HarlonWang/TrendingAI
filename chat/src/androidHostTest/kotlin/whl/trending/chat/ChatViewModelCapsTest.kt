package whl.trending.chat

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import whl.trending.chat.engine.ChatEngine
import whl.trending.chat.host.chatHost
import whl.trending.chat.sample.DemoChatHost
import whl.trending.chat.model.ChatMessage
import whl.trending.chat.model.ChatModelCaps
import whl.trending.chat.model.ChatModelOption
import whl.trending.chat.model.ChatModelsResponse
import whl.trending.chat.model.FOLLOW_SERVER_DEFAULT
import whl.trending.chat.model.SearchEvent

/** 能力位驱动的入口显隐：切到不支持搜索的模型时搜索开关收回、且不可再开。 */
class ChatViewModelCapsTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        // addPendingImage 读宿主的图片上限；用 Demo 宿主装上，模型选择流仍由构造参数注入
        chatHost = DemoChatHost
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private object NoopEngine : ChatEngine {
        override suspend fun send(
            history: List<ChatMessage>,
            onDelta: (String) -> Unit,
            search: Boolean,
            onSearch: (SearchEvent) -> Unit,
        ): String = ""
    }

    private val openai = ChatModelOption(id = "gpt-5.6-luna", name = "GPT-5.6 Luna")
    private val deepseek = ChatModelOption(
        id = "deepseek-v4-flash", name = "DeepSeek V4 Flash",
        provider = "deepseek", providerName = "DeepSeek",
        caps = ChatModelCaps(images = false, search = false),
    )
    private val catalog = ChatModelsResponse(models = listOf(openai, deepseek), default = openai.id)

    private fun vm(choice: MutableStateFlow<String>) = ChatViewModel(
        NoopEngine,
        loadModels = { catalog },
        track = {},
        modelSelection = { choice.map { it to false } },
    )

    @Test
    fun `默认模型全能力：搜索可开`() = runTest(dispatcher) {
        val viewModel = vm(MutableStateFlow(FOLLOW_SERVER_DEFAULT))
        advanceUntilIdle()
        assertEquals(ChatModelCaps(true, true), viewModel.currentCaps.value)
        viewModel.toggleWebSearch()
        assertTrue(viewModel.searchEnabled.value)
    }

    @Test
    fun `切到不支持搜索的模型：已开的搜索收回，且不可再开`() = runTest(dispatcher) {
        val choice = MutableStateFlow(FOLLOW_SERVER_DEFAULT)
        val viewModel = vm(choice)
        advanceUntilIdle()
        viewModel.toggleWebSearch()
        assertTrue(viewModel.searchEnabled.value)

        choice.value = deepseek.id
        advanceUntilIdle()
        assertEquals(ChatModelCaps(false, false), viewModel.currentCaps.value)
        assertFalse(viewModel.searchEnabled.value)
        viewModel.toggleWebSearch()
        assertFalse(viewModel.searchEnabled.value)
    }

    @Test
    fun `切到不接受图片的模型：待发图片清空，且此后新增被忽略`() = runTest(dispatcher) {
        val choice = MutableStateFlow(FOLLOW_SERVER_DEFAULT)
        val viewModel = vm(choice)
        advanceUntilIdle()
        viewModel.addPendingImage("/cache/a.jpg")
        assertEquals(listOf("/cache/a.jpg"), viewModel.uiState.value.pendingImages)

        choice.value = deepseek.id
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.pendingImages.isEmpty())
        viewModel.addPendingImage("/cache/b.jpg") // 异步选图在切换后才返回
        assertTrue(viewModel.uiState.value.pendingImages.isEmpty())

        choice.value = FOLLOW_SERVER_DEFAULT
        advanceUntilIdle()
        viewModel.addPendingImage("/cache/c.jpg")
        assertEquals(listOf("/cache/c.jpg"), viewModel.uiState.value.pendingImages)
    }

    @Test
    fun `无宿主（选择流缺席）时能力位保持全开`() = runTest(dispatcher) {
        val viewModel = ChatViewModel(NoopEngine, loadModels = { catalog }, track = {})
        advanceUntilIdle()
        assertEquals(ChatModelCaps(true, true), viewModel.currentCaps.value)
    }
}
