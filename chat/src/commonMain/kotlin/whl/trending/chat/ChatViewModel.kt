package whl.trending.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import whl.trending.chat.core.epochMillis
import whl.trending.chat.engine.ChatEngine
import whl.trending.chat.engine.ChatException
import whl.trending.chat.engine.VoiceTranscriber
import whl.trending.chat.host.ChatAiEvent
import whl.trending.chat.host.ChatAiOutcome
import whl.trending.chat.host.ChatVoiceOutcome
import whl.trending.chat.host.chatHost
import whl.trending.chat.model.ChatError
import whl.trending.chat.model.ChatErrorCategory
import whl.trending.chat.model.ChatMessage
import whl.trending.chat.model.ChatModelsProvider
import whl.trending.chat.model.ChatModelsResponse
import whl.trending.chat.model.ChatUiState
import whl.trending.chat.model.Role
import whl.trending.chat.model.SearchEvent
import whl.trending.chat.model.SourceRef
import whl.trending.chat.model.resolveDisplayedChatModel
import whl.trending.chat.store.ChatStore
import whl.trending.chat.store.InMemoryChatStore
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM

/** 抽屉里的一条会话概要 */
data class ThreadSummary(val id: Long, val title: String, val updatedAt: Long)

/** 语音录入需要就地反馈的结果（成功即发送，无需通知）。 */
enum class VoiceNotice { EMPTY, FAILED, PRO_REQUIRED }

/**
 * 聊天 ViewModel。并发模型是理解一切的钥匙：
 *
 * - **所有状态变更串行化**：会改 [uiState]/[currentThreadId] 的操作一律经
 *   [locked] 排队，同一时刻只有一个在跑，挂起点之间不会被别的操作交错。
 * - **单一真相源**：内存里只保留当前会话的消息（[uiState].messages 即真相），历史归 store；
 *   消息 id 就是 store 行 id（全局唯一），唯一的例外是流式占位（固定 [PLACEHOLDER_ID]，
 *   终局时被落库行替换）。
 * - **切走即取消**：在途流只属于当前会话（切会话/新会话都会取消它，已渲染部分
 *   落为可重试的中断错误行）。因此任何时刻至多一条在途流（[inFlight]）。
 *
 * 会话语义：VM 挂 Activity 作用域（`viewModel(key="chat")`），进程内再次进入续接现场
 * （返回首页查个东西再进来不重置）；新会话只经抽屉「新会话」或进程重启产生。
 *
 * @param store 持久化层；默认内存实现（Demo/预览），正式宿主注入 Room 实现
 * @param selectedModelId 应答模型记录用（展示「哪个模型答的」）；默认读全局设置的用户选择
 * @param transcriber 语音转写；null 时语音入口不可用（Demo）
 */
class ChatViewModel(
    private val engine: ChatEngine,
    initialMessages: List<ChatMessage> = emptyList(),
    private val store: ChatStore = InMemoryChatStore(),
    private val transcriber: VoiceTranscriber? = null,
    private val loadModels: suspend () -> ChatModelsResponse = { ChatModelsProvider.get() },
    private val track: (ChatAiEvent) -> Unit = { chatHost.onAiEvent(it) },
    private val selectedModelId: () -> String? = {
        // 留痕记「实际生效」而非「手选值」：未手选时手选值是空哨兵，实际用的是目录里的免费默认项
        runCatching {
            resolveDisplayedChatModel(
                ChatModelsProvider.cachedOrEmpty(),
                chatHost.currentChatModelChoice(),
                chatHost.currentIsPro(),
            )?.id
        }.getOrNull()
    },
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState(messages = initialMessages))
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    // 可选模型目录 + 服务端默认（驱动模型选择器）。拉取失败保持空目录 → 选择器隐藏、请求不带 model。
    private val _catalog = MutableStateFlow(ChatModelsResponse())
    val catalog: StateFlow<ChatModelsResponse> = _catalog.asStateFlow()

    /** 会话列表（抽屉数据源） */
    val threads: StateFlow<List<ThreadSummary>> =
        store.threads().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _currentThreadId = MutableStateFlow<Long?>(null)
    val currentThreadId: StateFlow<Long?> = _currentThreadId.asStateFlow()

    /** 联网搜索开关（P2）。粘滞语义：开启后对后续每条消息生效，直到手动关闭 */
    private val _searchEnabled = MutableStateFlow(false)
    val searchEnabled: StateFlow<Boolean> = _searchEnabled.asStateFlow()

    fun toggleWebSearch() {
        _searchEnabled.value = !_searchEnabled.value
    }

    val voiceAvailable: Boolean get() = transcriber != null

    private val _voiceNotices = MutableSharedFlow<VoiceNotice>(extraBufferCapacity = 1)
    val voiceNotices: SharedFlow<VoiceNotice> = _voiceNotices.asSharedFlow()

    private val stateLock = Mutex()

    private class InFlightSend(val threadId: Long, val startedAt: Long, val job: Job)

    private var inFlight: InFlightSend? = null

    init {
        viewModelScope.launch {
            _catalog.value = runCatching { loadModels() }.getOrDefault(ChatModelsResponse())
        }
    }

    /** 状态变更的唯一入口：viewModelScope（主线程）+ [stateLock] 串行执行 */
    private fun locked(block: suspend () -> Unit) {
        viewModelScope.launch { stateLock.withLock { block() } }
    }

    fun updateInput(text: String) {
        _uiState.update { it.copy(input = text) }
    }

    /** 追加一张已压缩好的待发图片（本地缓存路径），超过单条上限时忽略。 */
    fun addPendingImage(path: String) {
        _uiState.update {
            if (it.pendingImages.size >= maxImagesPerMessage() || path in it.pendingImages) it
            else it.copy(pendingImages = it.pendingImages + path)
        }
    }

    fun removePendingImage(path: String) {
        _uiState.update { it.copy(pendingImages = it.pendingImages - path) }
    }

    fun send() {
        val state = _uiState.value
        if (!state.canSend) return
        val text = state.input.trim()
        val images = state.pendingImages
        _uiState.update { it.copy(input = "", pendingImages = emptyList()) }
        queueChat(text, images, from = "input")
    }

    /** 发送一段指定文本（如建议动作的预设问题），不依赖输入框；发送中或空白则忽略。 */
    fun sendText(text: String) = queueChat(text, emptyList(), from = "quick_reply")

    /**
     * 语音录入：转写成文本后直接发送（听写不进输入框）。音频用完即删，失败也删。
     * 转写在途期间 [ChatUiState.isTranscribing] 为真，输入区据此禁发与显示等待态。
     */
    fun sendVoice(recording: whl.trending.chat.attach.VoiceRecording) {
        val transcriber = transcriber ?: return
        if (_uiState.value.isSending || _uiState.value.isTranscribing) return
        _uiState.update { it.copy(isTranscribing = true) }
        viewModelScope.launch {
            val outcome = try {
                val text = transcriber.transcribe(recording.path, recording.durationMs).text.trim()
                if (text.isEmpty()) {
                    _voiceNotices.tryEmit(VoiceNotice.EMPTY)
                    ChatVoiceOutcome.EMPTY
                } else {
                    _uiState.update { it.copy(isTranscribing = false) }
                    queueChat(text, emptyList(), from = "voice")
                    ChatVoiceOutcome.SENT
                }
            } catch (e: ChatException) {
                _voiceNotices.tryEmit(
                    if (e.error.code == ChatError.CODE_VOICE_REQUIRES_PRO) VoiceNotice.PRO_REQUIRED else VoiceNotice.FAILED,
                )
                ChatVoiceOutcome.ERROR
            } finally {
                _uiState.update { it.copy(isTranscribing = false) }
                runCatching { FileSystem.SYSTEM.delete(recording.path.toPath()) }
            }
            reportVoiceOutcome(outcome, recording.durationMs)
        }
    }

    /** 转写前就结束的语音结果（取消 / 太短 / 权限 / Pro 闸）由 UI 上报；转写后的由 [sendVoice] 上报。 */
    fun reportVoiceOutcome(outcome: ChatVoiceOutcome, durationMs: Long? = null) {
        track(ChatAiEvent.VoiceInput(outcome = outcome, durationMs = durationMs))
    }

    private fun queueChat(text: String, images: List<String>, from: String) {
        if (_uiState.value.isSending || (text.isBlank() && images.isEmpty())) return
        // 发送被接受那一刻捕获搜索开关：排队与流启动之间用户再切开关不影响本条
        val search = _searchEnabled.value
        locked {
            if (_uiState.value.isSending) return@locked
            trackChatSend(from, images.size)
            _uiState.update { it.copy(isSending = true) }
            val threadId = ensureThread(text)
            appendVisible(store.appendUserMessage(threadId, text, images))
            startStream(threadId, search)
        }
    }

    /** 对可重试的失败消息重试：移除该错误条，重打一次请求。
     *  quota_device 例外放行：触顶之后完成登录（或次日额度恢复），重发即可继续。 */
    fun retry(message: ChatMessage) {
        val error = message.error ?: return
        if (!error.category.retryable && error.code != ChatError.CODE_QUOTA_DEVICE) return
        locked {
            // 错误条仍须在场（防连点与过期引用）
            if (_uiState.value.isSending || _uiState.value.messages.none { it.id == message.id }) return@locked
            val threadId = _currentThreadId.value ?: return@locked
            store.deleteMessage(message.id)
            removeVisible(message.id)
            // 重试必然重打一次请求，要补 ai_requested——不补则终局的 ai_completed 落单，
            // 成对关系一破，「请求数」这个分母就再也不准
            val resent = _uiState.value.messages.lastOrNull { it.role == Role.USER }
            trackChatSend(from = "retry", imageCount = resent?.images?.size ?: 0)
            _uiState.update { it.copy(isSending = true) }
            startStream(threadId, _searchEnabled.value)
        }
    }

    /**
     * 发送埋点。落点与后端 `chat_logs` 的写入时机对齐——那边记在配额检查之前，
     * 被限流 / 上游报错的请求同样留痕，所以这里也在发出前记，两侧行数可直接对账，
     * 残差只剩上报丢失一项。
     *
     * [from]：`input`（输入框）/ `quick_reply`（建议动作）/ `retry`（重试）/ `voice`（语音转写）。
     */
    private fun trackChatSend(from: String, imageCount: Int) {
        track(ChatAiEvent.Requested(from = from, imageCount = imageCount))
    }

    // 会话管理（抽屉）

    fun switchThread(id: Long) = locked {
        if (id == _currentThreadId.value) return@locked
        cancelInFlight(persistInterrupted = true)
        openThread(id)
    }

    /** 抽屉「新会话」：总是全新开始，不复用任何历史 */
    fun startNewThread() = locked {
        cancelInFlight(persistInterrupted = true)
        resetSession()
    }

    fun renameThread(id: Long, title: String) = locked {
        store.renameThread(id, title)
    }

    fun deleteThread(id: Long) = locked {
        if (inFlight?.threadId == id) cancelInFlight(persistInterrupted = false)
        store.deleteThread(id)
        if (_currentThreadId.value == id) resetSession()
    }

    // 流式管线

    /**
     * 统一请求路径：先追加流式占位（[PLACEHOLDER_ID]），delta 到达时增量更新其 content；
     * 终局在 [stateLock] 内收口——成功以全文落库并替换占位，失败落错误行（整条重试）。
     * 流协程只在占位上做原子更新，不碰其他状态，取消它无需回滚。
     */
    private fun startStream(threadId: Long, search: Boolean) {
        val startedAt = epochMillis()
        _uiState.update {
            it.copy(messages = it.messages + ChatMessage(PLACEHOLDER_ID, Role.ASSISTANT, ""))
        }
        val job = viewModelScope.launch {
            val result = runCatching {
                // 空 assistant 行（错误条）不进 history：对模型无信息量，且上游可能拒空 content
                val history = _uiState.value.messages.filterNot {
                    it.id == PLACEHOLDER_ID || (it.role == Role.ASSISTANT && it.content.isBlank())
                }
                engine.send(
                    history,
                    onDelta = { appendDelta(it) },
                    search = search,
                    onSearch = { applySearchEvent(it) },
                )
            }
            // 取消不是失败：runCatching 连 CancellationException 一起吞，必须重抛——
            // 终局收口交给取消方（cancelInFlight），这里再进锁会与它互等
            result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            stateLock.withLock { finishStream(threadId, result, startedAt) }
        }
        inFlight = InFlightSend(threadId, startedAt, job)
    }

    private suspend fun finishStream(threadId: Long, result: Result<String>, startedAt: Long) {
        inFlight = null
        result.fold(
            onSuccess = { full ->
                if (full.isBlank()) {
                    // 空回复（如内容过滤拒答）：不落库也不留幽灵气泡
                    removeVisible(PLACEHOLDER_ID)
                } else {
                    val sources = _uiState.value.messages
                        .firstOrNull { it.id == PLACEHOLDER_ID }?.sources.orEmpty()
                    replaceVisible(
                        PLACEHOLDER_ID,
                        store.appendAssistantMessage(threadId, full, selectedModelId(), sources),
                    )
                }
                track(
                    ChatAiEvent.Completed(
                        ChatAiOutcome.OK,
                        durationMs = epochMillis() - startedAt,
                    ),
                )
            },
            onFailure = { e ->
                val error = (e as? ChatException)?.error
                    ?: ChatError(ChatErrorCategory.UNKNOWN, detail = e.toString())
                track(failureEvent(error, epochMillis() - startedAt))
                // 已渲染部分丢弃，整条重试（中途断流语义）
                replaceVisible(PLACEHOLDER_ID, store.appendErrorMessage(threadId, error))
            },
        )
        _uiState.update { it.copy(isSending = false) }
    }

    /**
     * 取消在途流（持锁调用）。[persistInterrupted]：会话还在（切换/重置）时落一条可重试的
     * 中断错误行，用户切回能看到「回复被打断」而非凭空少一条；线程将删时不落。
     * 无论哪种都补 ai_completed(interrupted)——取消也是终态，不补则与 ai_requested 失配。
     */
    private suspend fun cancelInFlight(persistInterrupted: Boolean) {
        val flight = inFlight ?: return
        inFlight = null
        // 流协程若正等本锁，cancel 会把它从锁等待中打断，不会互等
        flight.job.cancel()
        flight.job.join()
        removeVisible(PLACEHOLDER_ID)
        _uiState.update { it.copy(isSending = false) }
        track(
            ChatAiEvent.Completed(
                ChatAiOutcome.INTERRUPTED,
                durationMs = epochMillis() - flight.startedAt,
                reason = "canceled",
            ),
        )
        if (persistInterrupted) {
            val error = ChatError(
                ChatErrorCategory.SERVER,
                code = ChatError.CODE_STREAM_INTERRUPTED,
                detail = "canceled: session switched away",
            )
            store.appendErrorMessage(flight.threadId, error)
        }
    }

    // 会话状态（持锁调用）

    private suspend fun openThread(id: Long) {
        val messages = store.loadMessages(id)
        _currentThreadId.value = id
        _uiState.update {
            it.copy(messages = messages, isSending = false, pendingImages = emptyList())
        }
    }

    private fun resetSession() {
        _currentThreadId.value = null
        _uiState.update {
            it.copy(messages = emptyList(), isSending = false, input = "", pendingImages = emptyList())
        }
    }

    /** 首条消息才建线（懒建）；当前线已存在则直接复用 */
    private suspend fun ensureThread(firstMessageText: String): Long {
        _currentThreadId.value?.let { return it }
        val id = store.createThread(firstMessageText)
        _currentThreadId.value = id
        return id
    }

    // 可见列表的原子更新（消息 id 全局唯一，按 id 定位即可）

    private fun appendVisible(message: ChatMessage) {
        _uiState.update { it.copy(messages = it.messages + message) }
    }

    private fun removeVisible(messageId: Long) {
        _uiState.update { s -> s.copy(messages = s.messages.filterNot { it.id == messageId }) }
    }

    private fun replaceVisible(messageId: Long, replacement: ChatMessage) {
        _uiState.update { s ->
            s.copy(messages = s.messages.map { if (it.id == messageId) replacement else it })
        }
    }

    /** 流式增量与搜索事件只落在占位上：单次原子更新，无锁也不会与串行操作交错出错态 */
    private fun updatePlaceholder(transform: (ChatMessage) -> ChatMessage) {
        _uiState.update { s ->
            s.copy(messages = s.messages.map { if (it.id == PLACEHOLDER_ID) transform(it) else it })
        }
    }

    private fun appendDelta(delta: String) =
        updatePlaceholder { it.copy(content = it.content + delta) }

    private fun applySearchEvent(event: SearchEvent) = updatePlaceholder { m ->
        when (event) {
            is SearchEvent.Started -> m.copy(searching = true)
            is SearchEvent.Done -> m.copy(searching = false)
            is SearchEvent.Source ->
                m.copy(sources = (m.sources + SourceRef(event.title, event.url)).distinctBy { it.url })
        }
    }

    companion object {
        /** 单条消息图片数上限：服务端 app-config 下发（KV 单源），未拉到用与服务端一致的默认 */
        fun maxImagesPerMessage(): Int = chatHost.imagesMaxCount()

        /** 流式占位的保留 id：store 行 id 恒为正，负值永不与真实消息撞号 */
        private const val PLACEHOLDER_ID = -1L
    }
}
