package whl.trending.chat

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import whl.trending.chat.attach.VoiceRecording
import whl.trending.chat.engine.ChatEngine
import whl.trending.chat.engine.ChatException
import whl.trending.chat.engine.Transcription
import whl.trending.chat.engine.VoiceTranscriber
import whl.trending.chat.host.ChatAiEvent
import whl.trending.chat.host.ChatVoiceOutcome
import whl.trending.chat.model.ChatError
import whl.trending.chat.model.ChatErrorCategory
import whl.trending.chat.model.ChatMessage
import whl.trending.chat.model.ChatModelsResponse
import whl.trending.chat.model.Role

/** 语音录入：转写后直接发送、空文本与失败的就地反馈、音频用后即删、埋点成对。 */
class ChatViewModelVoiceTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private class EchoEngine : ChatEngine {
        val sent = mutableListOf<String>()
        override suspend fun send(
            history: List<ChatMessage>,
            onDelta: (String) -> Unit,
            search: Boolean,
            onSearch: (whl.trending.chat.model.SearchEvent) -> Unit,
        ): String {
            sent += history.last { it.role == Role.USER }.content
            onDelta("ok")
            return "ok"
        }
    }

    private class ScriptedTranscriber(var text: String = "你好", var failWith: ChatError? = null) : VoiceTranscriber {
        val calls = mutableListOf<Pair<String, Long>>()
        override suspend fun transcribe(path: String, durationMs: Long): Transcription {
            calls += path to durationMs
            failWith?.let { throw ChatException(it) }
            return Transcription(text)
        }
    }

    private fun tempAudio(): File = File.createTempFile("voice", ".m4a").apply { writeBytes(ByteArray(16)) }

    private fun vm(engine: ChatEngine, transcriber: VoiceTranscriber?, events: MutableList<ChatAiEvent> = mutableListOf()) =
        ChatViewModel(engine, loadModels = { ChatModelsResponse() }, track = { events += it }, transcriber = transcriber)

    @Test
    fun `转写成功：文本直接发送，音频删除，voice_input sent 与 ai_requested from=voice 各一条`() = runTest(dispatcher) {
        val engine = EchoEngine()
        val events = mutableListOf<ChatAiEvent>()
        val viewModel = vm(engine, ScriptedTranscriber(" 帮我看看 Kubernetes "), events)
        val audio = tempAudio()
        viewModel.sendVoice(VoiceRecording(audio.absolutePath, 4200))
        advanceUntilIdle()

        assertEquals(listOf("帮我看看 Kubernetes"), engine.sent)
        assertFalse(audio.exists())
        assertFalse(viewModel.uiState.value.isTranscribing)
        assertEquals(
            listOf(ChatAiEvent.Requested(from = "voice", imageCount = 0)),
            events.filterIsInstance<ChatAiEvent.Requested>(),
        )
        assertEquals(
            listOf(ChatAiEvent.VoiceInput(ChatVoiceOutcome.SENT, 4200)),
            events.filterIsInstance<ChatAiEvent.VoiceInput>(),
        )
    }

    @Test
    fun `转写为空：不发送，提示 EMPTY，音频仍删除`() = runTest(dispatcher) {
        val engine = EchoEngine()
        val events = mutableListOf<ChatAiEvent>()
        val viewModel = vm(engine, ScriptedTranscriber("   "), events)
        val notices = mutableListOf<VoiceNotice>()
        val collector = launch { viewModel.voiceNotices.collect { notices += it } }
        val audio = tempAudio()
        viewModel.sendVoice(VoiceRecording(audio.absolutePath, 1500))
        advanceUntilIdle()
        collector.cancel()

        assertTrue(engine.sent.isEmpty())
        assertEquals(listOf(VoiceNotice.EMPTY), notices)
        assertFalse(audio.exists())
        assertTrue(viewModel.uiState.value.messages.isEmpty())
        assertEquals(listOf<ChatAiEvent>(ChatAiEvent.VoiceInput(ChatVoiceOutcome.EMPTY, 1500)), events)
    }

    @Test
    fun `转写失败：提示 FAILED，isTranscribing 复位；403 Pro 闸提示 PRO_REQUIRED`() = runTest(dispatcher) {
        val transcriber = ScriptedTranscriber(failWith = ChatError(ChatErrorCategory.SERVER, code = "upstream_error"))
        val events = mutableListOf<ChatAiEvent>()
        val viewModel = vm(EchoEngine(), transcriber, events)
        val notices = mutableListOf<VoiceNotice>()
        val collector = launch { viewModel.voiceNotices.collect { notices += it } }

        viewModel.sendVoice(VoiceRecording(tempAudio().absolutePath, 2000))
        advanceUntilIdle()
        transcriber.failWith = ChatError(ChatErrorCategory.BAD_REQUEST, code = ChatError.CODE_VOICE_REQUIRES_PRO, httpStatus = 403)
        viewModel.sendVoice(VoiceRecording(tempAudio().absolutePath, 2000))
        advanceUntilIdle()
        collector.cancel()

        assertEquals(listOf(VoiceNotice.FAILED, VoiceNotice.PRO_REQUIRED), notices)
        assertFalse(viewModel.uiState.value.isTranscribing)
        assertEquals(
            listOf(ChatVoiceOutcome.ERROR, ChatVoiceOutcome.PRO_GATE),
            events.filterIsInstance<ChatAiEvent.VoiceInput>().map { it.outcome },
        )
    }

    @Test
    fun `转写在途禁发：isTranscribing 为真时 canSend 为假，第二次 sendVoice 被忽略`() = runTest(dispatcher) {
        val transcriber = ScriptedTranscriber()
        val viewModel = vm(EchoEngine(), transcriber)
        viewModel.sendVoice(VoiceRecording(tempAudio().absolutePath, 2000))
        viewModel.updateInput("typed")
        assertTrue(viewModel.uiState.value.isTranscribing)
        assertFalse(viewModel.uiState.value.canSend)
        viewModel.sendVoice(VoiceRecording(tempAudio().absolutePath, 2000))
        advanceUntilIdle()
        assertEquals(1, transcriber.calls.size)
    }

    @Test
    fun `转写在途：建议动作与重试的发送被门禁拒绝，转写文本随后照常发出`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val transcriber = object : VoiceTranscriber {
            override suspend fun transcribe(path: String, durationMs: Long): Transcription {
                gate.await()
                return Transcription("语音内容")
            }
        }
        val engine = EchoEngine()
        val events = mutableListOf<ChatAiEvent>()
        val viewModel = vm(engine, transcriber, events)
        viewModel.sendVoice(VoiceRecording(tempAudio().absolutePath, 3000))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isBusy)

        viewModel.sendText("chip")
        advanceUntilIdle()
        assertTrue(engine.sent.isEmpty())

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("语音内容"), engine.sent)
        assertEquals(listOf("voice"), events.filterIsInstance<ChatAiEvent.Requested>().map { it.from })
        assertEquals(
            listOf(ChatVoiceOutcome.SENT),
            events.filterIsInstance<ChatAiEvent.VoiceInput>().map { it.outcome },
        )
    }

    @Test
    fun `未注入转写器：voiceAvailable 为假，sendVoice 无副作用`() = runTest(dispatcher) {
        val engine = EchoEngine()
        val viewModel = vm(engine, transcriber = null)
        assertFalse(viewModel.voiceAvailable)
        viewModel.sendVoice(VoiceRecording(tempAudio().absolutePath, 2000))
        advanceUntilIdle()
        assertTrue(engine.sent.isEmpty())
        assertFalse(viewModel.uiState.value.isTranscribing)
    }
}
