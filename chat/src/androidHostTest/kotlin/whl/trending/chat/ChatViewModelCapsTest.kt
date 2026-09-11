package whl.trending.chat

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import whl.trending.chat.engine.ChatEngine
import whl.trending.chat.model.ChatMessage
import whl.trending.chat.model.ChatModelCaps
import whl.trending.chat.model.ChatModelOption
import whl.trending.chat.model.ChatModelsResponse
import whl.trending.chat.model.FOLLOW_SERVER_DEFAULT
import whl.trending.chat.model.ImageEvent
import whl.trending.chat.model.SearchEvent

/** 能力位驱动的入口显隐：切到不支持搜索的模型时搜索开关收回、且不可再开。 */
class ChatViewModelCapsTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private object NoopEngine : ChatEngine {
        override suspend fun send(
            history: List<ChatMessage>,
            onDelta: (String) -> Unit,
            search: Boolean,
            onSearch: (SearchEvent) -> Unit,
            image: Boolean,
            onImage: (ImageEvent) -> Unit,
            ): String = ""
    }

    private val openai = ChatModelOption(id = "gpt-5.6-luna", name = "GPT-5.6 Luna", caps = ChatModelCaps(imageGeneration = true))
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
        assertEquals(ChatModelCaps(true, true, imageGeneration = true), viewModel.currentCaps.value)
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
    fun `生图开关：目录声明 imageGeneration 才可开，切到不支持的模型收回`() = runTest(dispatcher) {
        val choice = MutableStateFlow(FOLLOW_SERVER_DEFAULT)
        val viewModel = vm(choice)
        advanceUntilIdle()
        viewModel.toggleImageGeneration()
        assertTrue(viewModel.imageEnabled.value)

        choice.value = deepseek.id
        advanceUntilIdle()
        assertFalse(viewModel.imageEnabled.value)
        viewModel.toggleImageGeneration()
        assertFalse(viewModel.imageEnabled.value)
    }

    @Test
    fun `生图与搜索互斥：开一个收另一个`() = runTest(dispatcher) {
        val viewModel = vm(MutableStateFlow(FOLLOW_SERVER_DEFAULT))
        advanceUntilIdle()
        viewModel.toggleWebSearch()
        viewModel.toggleImageGeneration()
        assertTrue(viewModel.imageEnabled.value)
        assertFalse(viewModel.searchEnabled.value)
        viewModel.toggleWebSearch()
        assertTrue(viewModel.searchEnabled.value)
        assertFalse(viewModel.imageEnabled.value)
    }

    /** 旧服务端不下发 imageGeneration：缺省 false，入口不亮 */
    @Test
    fun `目录未声明 imageGeneration 时生图不可开`() = runTest(dispatcher) {
        val legacy = ChatModelsResponse(models = listOf(ChatModelOption(id = "gpt-5.6-luna")), default = "gpt-5.6-luna")
        val viewModel = ChatViewModel(NoopEngine, loadModels = { legacy }, track = {}, modelSelection = { MutableStateFlow(FOLLOW_SERVER_DEFAULT).map { it to false } })
        advanceUntilIdle()
        assertFalse(viewModel.currentCaps.value.imageGeneration)
        viewModel.toggleImageGeneration()
        assertFalse(viewModel.imageEnabled.value)
    }

    @Test
    fun `选择流抛错（无宿主）时能力位保持全开`() = runTest(dispatcher) {
        val viewModel = ChatViewModel(
            NoopEngine, loadModels = { catalog }, track = {},
            modelSelection = { flow { error("chatHost not installed") } },
        )
        advanceUntilIdle()
        assertEquals(ChatModelCaps(true, true), viewModel.currentCaps.value)
    }
}
