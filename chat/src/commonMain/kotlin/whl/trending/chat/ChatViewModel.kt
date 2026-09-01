package whl.trending.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import whl.trending.chat.core.epochMillis
import whl.trending.chat.engine.ChatEngine
import whl.trending.chat.engine.ChatException
import whl.trending.chat.host.ChatAiEvent
import whl.trending.chat.host.ChatAiKind
import whl.trending.chat.host.ChatAiOutcome
import whl.trending.chat.host.chatHost
import whl.trending.chat.model.ChatError
import whl.trending.chat.model.ChatErrorCategory
import whl.trending.chat.model.ChatMessage
import whl.trending.chat.model.ChatMode
import whl.trending.chat.model.ChatModelsProvider
import whl.trending.chat.model.ChatModelsResponse
import whl.trending.chat.model.ChatUiState
import whl.trending.chat.model.MessageKind
import whl.trending.chat.model.Role
import whl.trending.chat.model.SearchEvent
import whl.trending.chat.model.SourceRef
import whl.trending.chat.model.resolveDisplayedChatModel
import whl.trending.chat.store.ChatStore
import whl.trending.chat.store.InMemoryChatStore

/** 抽屉里的一条会话概要 */
data class ThreadSummary(val id: Long, val title: String, val updatedAt: Long)

/**
 * 聊天 ViewModel。并发模型是理解一切的钥匙：
 *
 * - **所有状态变更串行化**：会改 [uiState]/[currentThreadId]/[activeContext] 的操作一律经
 *   [locked] 排队，同一时刻只有一个在跑，挂起点之间不会被别的操作交错。
 * - **单一真相源**：内存里只保留当前会话的消息（[uiState].messages 即真相），历史归 store；
 *   消息 id 就是 store 行 id（全局唯一），唯一的例外是流式占位（固定 [PLACEHOLDER_ID]，
 *   终局时被落库行替换）。
 * - **切走即取消**：在途流只属于当前会话（切会话/新会话/换入口都会取消它，已渲染部分
 *   落为可重试的中断错误行）。因此任何时刻至多一条在途流（[inFlight]）。
 *   Deep Research 例外——任务是服务端资产，独立于会话切换（见 [ResearchRunner]）。
 *
 * 会话语义：入口进入总是新会话（历史进抽屉），仅同入口的进程内再进入续接现场——
 * 本 VM 挂 Activity 作用域（`viewModel(key="chat")`），返回首页再进来不该重置刚才的对话。
 *
 * @param store 持久化层；默认内存实现（Demo/预览），正式宿主注入 Room 实现
 * @param selectedModelId 应答模型记录用（展示「哪个模型答的」）；默认读全局设置的用户选择
 */
class ChatViewModel(
    private val engine: ChatEngine,
    initialContext: ChatContext? = null,
    initialMessages: List<ChatMessage> = emptyList(),
    private val store: ChatStore = InMemoryChatStore(),
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

    /** 会话能力模式（P2：联网搜索）。粘滞语义：开启后对后续每条消息生效，直到手动关闭 */
    private val _chatMode = MutableStateFlow<ChatMode>(ChatMode.Normal)
    val chatMode: StateFlow<ChatMode> = _chatMode.asStateFlow()

    /** EchoFlow 单选互斥范式：再点同项回 Normal，点别项自动顶掉 */
    fun toggleWebSearch() = toggleMode(ChatMode.WebSearch)
    fun toggleDeepResearch() = toggleMode(ChatMode.DeepResearch)

    private fun toggleMode(mode: ChatMode) {
        _chatMode.value = if (_chatMode.value == mode) ChatMode.Normal else mode
    }

    /** 当前会话的 ChatContext（解读 chip / 服务端 context 注入）；随切换/重置而变 */
    private var activeContext: ChatContext? = initialContext

    private val stateLock = Mutex()

    private class InFlightSend(val threadId: Long, val kind: MessageKind, val startedAt: Long, val job: Job)

    private var inFlight: InFlightSend? = null

    private val research = ResearchRunner(viewModelScope, engine, store, track, ::applyToVisible)

    init {
        viewModelScope.launch {
            _catalog.value = runCatching { loadModels() }.getOrDefault(ChatModelsResponse())
        }
        // 落在 init 而非 enterEntry：Activity 被系统重建时 Screen 侧的 enteredKey
        // （rememberSaveable）已恢复、enterEntry 不会再调，而那恰恰是最需要恢复的场景
        locked { research.resumeAll() }
    }

    /** 状态变更的唯一入口：viewModelScope（主线程）+ [stateLock] 串行执行 */
    private fun locked(block: suspend () -> Unit) {
        viewModelScope.launch { stateLock.withLock { block() } }
    }

    /**
     * 入口进入（Screen 每个新入口调用一次）。总是新会话（对齐 ChatGPT / Gemini / Grok：
     * 对话是一次性任务单元，历史进抽屉、不进现场），唯一例外是**同入口的进程内续接**：
     * VM 挂 Activity 作用域，返回首页查个东西再进来，现场还在就不重置。
     */
    fun enterEntry(context: ChatContext?) = locked {
        val sameEntry = ChatStore.entryKeyOf(context) == ChatStore.entryKeyOf(activeContext)
        if (sameEntry && (_currentThreadId.value != null || _uiState.value.messages.isNotEmpty())) {
            activeContext = context
            return@locked
        }
        cancelInFlight(persistInterrupted = true)
        resetSession(context, clearInput = false)
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
        // research 仅支持文本：不消费待发图片（保留在输入区，用户可换模式再发或移除），
        // 避免静默丢弃（Sourcery 审查建议）
        if (_chatMode.value == ChatMode.DeepResearch) {
            if (text.isBlank()) return
            _uiState.update { it.copy(input = "") }
            queueResearch(text)
            return
        }
        val images = state.pendingImages
        _uiState.update { it.copy(input = "", pendingImages = emptyList()) }
        queueChat(text, images, from = "input")
    }

    /** 发送一段指定文本（如快捷按钮的预设问题），不依赖输入框；发送中或空白则忽略。 */
    fun sendText(text: String) = queueChat(text, emptyList(), from = "quick_reply")

    /**
     * 「一键详细解读」：插入一条 user 消息（chip 文案），走 detail 管线流式生成解读。
     * 可见性由 [DetailSummaryPolicy] 保证（GitHub 条目 + README 达标 + 尚无成功解读）。
     */
    fun sendDetailSummary(promptText: String) {
        if (_uiState.value.isSending || activeContext?.externalId == null) return
        locked {
            val context = activeContext
            if (_uiState.value.isSending || context?.externalId == null) return@locked
            track(ChatAiEvent.Requested(ChatAiKind.DETAIL_SUMMARY, from = "chat"))
            _uiState.update { it.copy(isSending = true) }
            val threadId = ensureThread(promptText)
            appendVisible(store.appendUserMessage(threadId, promptText, kind = MessageKind.DETAIL_SUMMARY))
            startStream(threadId, MessageKind.DETAIL_SUMMARY, context)
        }
    }

    private fun queueChat(text: String, images: List<String>, from: String) {
        if (_uiState.value.isSending || (text.isBlank() && images.isEmpty())) return
        if (_chatMode.value == ChatMode.DeepResearch) {
            queueResearch(text, from)
            return
        }
        locked {
            if (_uiState.value.isSending) return@locked
            trackChatSend(from, images.size)
            _uiState.update { it.copy(isSending = true) }
            val threadId = ensureThread(text)
            appendVisible(store.appendUserMessage(threadId, text, images))
            startStream(threadId, MessageKind.CHAT, activeContext)
        }
    }

    /** 对可重试的失败消息重试：移除该错误条，按消息 [MessageKind] 路由回对应管线。
     *  quota_device / login_required 例外放行：触顶或登录闸之后完成登录，重发即可继续。 */
    fun retry(message: ChatMessage) {
        val error = message.error ?: return
        val passthrough = error.code == ChatError.CODE_QUOTA_DEVICE ||
            error.code == ChatError.CODE_LOGIN_REQUIRED
        if (!error.category.retryable && !passthrough) return
        locked {
            // 错误条仍须在场（防连点与过期引用）
            if (_uiState.value.isSending || _uiState.value.messages.none { it.id == message.id }) return@locked
            if (message.kind == MessageKind.DEEP_RESEARCH) {
                retryResearch(message)
                return@locked
            }
            val threadId = _currentThreadId.value ?: return@locked
            store.deleteMessage(message.id)
            removeVisible(message.id)
            // 重试必然重打一次请求，两种 kind 都要补 ai_requested——不补则终局的
            // ai_completed 落单，成对关系一破，「请求数」这个分母就再也不准
            if (message.kind == MessageKind.CHAT) {
                val resent = _uiState.value.messages.lastOrNull { it.role == Role.USER }
                trackChatSend(from = "retry", imageCount = resent?.images?.size ?: 0)
            } else {
                track(ChatAiEvent.Requested(ChatAiKind.DETAIL_SUMMARY, from = "retry"))
            }
            _uiState.update { it.copy(isSending = true) }
            startStream(threadId, message.kind, activeContext)
        }
    }

    /**
     * chat 管线的发送埋点。落点与后端 `chat_logs` 的写入时机对齐——那边记在配额检查之前，
     * 被限流 / 上游报错的请求同样留痕，所以这里也在发出前记，两侧行数可直接对账，
     * 残差只剩上报丢失一项。对账时按 `kind=chat` 过滤：detail / research 走独立端点、不入该表。
     *
     * [from]：`input`（输入框）/ `quick_reply`（预设气泡）/ `retry`（重试）。
     */
    private fun trackChatSend(from: String, imageCount: Int) {
        track(
            ChatAiEvent.Requested(
                ChatAiKind.CHAT,
                from = from,
                imageCount = imageCount,
                // 与后端 hasContext（context && context.title）等价：ChatContext.title 非空
                hasContext = activeContext != null,
            ),
        )
    }

    // 会话管理（抽屉）

    fun switchThread(id: Long) = locked {
        if (id == _currentThreadId.value) return@locked
        cancelInFlight(persistInterrupted = true)
        openThread(id)
    }

    /** 抽屉「新会话」：总是全新开始（通用入口），不复用任何历史 */
    fun startNewThread() = locked {
        cancelInFlight(persistInterrupted = true)
        resetSession(context = null, clearInput = true)
    }

    fun renameThread(id: Long, title: String) = locked {
        store.renameThread(id, title)
    }

    fun deleteThread(id: Long) = locked {
        if (inFlight?.threadId == id) cancelInFlight(persistInterrupted = false)
        research.cancelForThread(id)
        store.deleteThread(id)
        if (_currentThreadId.value == id) resetSession(context = null, clearInput = true)
    }

    // Deep Research（P3）

    /** 解读卡尾部「深度调研此项目」升级入口：按 research 管线直发，不依赖模式开关 */
    fun sendRepoResearch(promptText: String) {
        if (_uiState.value.isSending || activeContext == null) return
        queueResearch(promptText, from = "detail_summary_upsell")
    }

    private fun queueResearch(topic: String, from: String = "chat") = locked {
        if (_uiState.value.isSending) return@locked
        track(ChatAiEvent.Requested(ChatAiKind.RESEARCH, from = from))
        _uiState.update { it.copy(isSending = true) }
        val threadId = ensureThread(topic)
        appendVisible(store.appendUserMessage(threadId, topic, kind = MessageKind.DEEP_RESEARCH))
        // 条目会话附上标题/链接锚点（发送与重试都传原文、各自重拼，保证同构）
        appendVisible(research.submit(threadId, ResearchTopics.compose(topic, activeContext)))
        // 提交落定即解锁输入：轮询可能持续小时级，期间会话仍可正常对话
        _uiState.update { it.copy(isSending = false) }
    }

    /** research 错误条的重试：任务已在服务端存在则恢复轮询同一 runId（绝不重复建任务——
     *  会重复扣费）；任务未建立（提交失败 / 空报告判死）则取相邻提问重新提交 */
    private suspend fun retryResearch(message: ChatMessage) {
        val threadId = _currentThreadId.value ?: return
        val runId = message.researchRunId
        if (runId != null) {
            track(ChatAiEvent.Requested(ChatAiKind.RESEARCH, from = "retry"))
            store.resetResearchPlaceholder(threadId, message.id, runId)
            applyToVisible(message.id) { it.copy(error = null, searching = true) }
            research.startPolling(threadId, message.id, runId)
            return
        }
        val messages = _uiState.value.messages
        val index = messages.indexOfFirst { it.id == message.id }
        val topic = messages.take(index.coerceAtLeast(0))
            .lastOrNull { it.role == Role.USER && it.kind == MessageKind.DEEP_RESEARCH }
            ?.content ?: return
        track(ChatAiEvent.Requested(ChatAiKind.RESEARCH, from = "retry"))
        store.deleteMessage(message.id)
        removeVisible(message.id)
        _uiState.update { it.copy(isSending = true) }
        appendVisible(research.submit(threadId, ResearchTopics.compose(topic, activeContext)))
        _uiState.update { it.copy(isSending = false) }
    }

    // 流式管线

    /**
     * 统一请求路径：先追加流式占位（[PLACEHOLDER_ID]），delta 到达时增量更新其 content；
     * 终局在 [stateLock] 内收口——成功以全文落库并替换占位，失败落错误行（整条重试）。
     * 流协程只在占位上做原子更新，不碰其他状态，取消它无需回滚。
     */
    private fun startStream(threadId: Long, kind: MessageKind, context: ChatContext?) {
        val startedAt = epochMillis()
        _uiState.update {
            it.copy(messages = it.messages + ChatMessage(PLACEHOLDER_ID, Role.ASSISTANT, "", kind = kind))
        }
        val job = viewModelScope.launch {
            var cacheHit = false
            val result = runCatching {
                when (kind) {
                    MessageKind.CHAT -> {
                        // 空 assistant 行（错误条 / research 占位）不进 history：对模型无信息量，
                        // 且上游可能拒空 content
                        val history = _uiState.value.messages.filterNot {
                            it.id == PLACEHOLDER_ID || (it.role == Role.ASSISTANT && it.content.isBlank())
                        }
                        engine.send(
                            history,
                            context,
                            onDelta = { appendDelta(it) },
                            search = _chatMode.value == ChatMode.WebSearch,
                            onSearch = { applySearchEvent(it) },
                        )
                    }
                    MessageKind.DETAIL_SUMMARY -> {
                        val detail = engine.sendDetailSummary(requireNotNull(context)) { appendDelta(it) }
                        cacheHit = detail.cached
                        detail.content
                    }
                    MessageKind.DEEP_RESEARCH -> error("research 走 ResearchRunner，不进流式管线")
                }
            }
            // 取消不是失败：runCatching 连 CancellationException 一起吞，必须重抛——
            // 终局收口交给取消方（cancelInFlight），这里再进锁会与它互等
            result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            stateLock.withLock { finishStream(threadId, kind, result, startedAt, cacheHit) }
        }
        inFlight = InFlightSend(threadId, kind, startedAt, job)
    }

    private suspend fun finishStream(
        threadId: Long,
        kind: MessageKind,
        result: Result<String>,
        startedAt: Long,
        cacheHit: Boolean,
    ) {
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
                        store.appendAssistantMessage(threadId, full, kind, selectedModelId(), sources),
                    )
                }
                track(
                    ChatAiEvent.Completed(
                        kind.toAiKind(),
                        if (cacheHit) ChatAiOutcome.CACHE_HIT else ChatAiOutcome.OK,
                        durationMs = epochMillis() - startedAt,
                    ),
                )
            },
            onFailure = { e ->
                val error = (e as? ChatException)?.error
                    ?: ChatError(ChatErrorCategory.UNKNOWN, detail = e.toString())
                track(failureEvent(kind, error, epochMillis() - startedAt))
                // 已渲染部分丢弃，整条重试（中途断流语义）
                replaceVisible(PLACEHOLDER_ID, store.appendErrorMessage(threadId, kind, error))
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
                flight.kind.toAiKind(),
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
            store.appendErrorMessage(flight.threadId, flight.kind, error)
        }
    }

    // 会话状态（持锁调用）

    private suspend fun openThread(id: Long) {
        val messages = store.loadMessages(id).map {
            if (it.kind == MessageKind.DEEP_RESEARCH && it.content.isBlank() &&
                it.researchRunId != null && it.error == null
            ) it.copy(searching = true) else it
        }
        _currentThreadId.value = id
        activeContext = store.contextOf(id)
        _uiState.update {
            it.copy(messages = messages, isSending = false, pendingImages = emptyList())
        }
        messages.filter { it.searching }.forEach { research.startPolling(id, it.id, it.researchRunId!!) }
    }

    private fun resetSession(context: ChatContext?, clearInput: Boolean) {
        activeContext = context
        _currentThreadId.value = null
        _uiState.update {
            it.copy(
                messages = emptyList(),
                isSending = false,
                input = if (clearInput) "" else it.input,
                pendingImages = emptyList(),
            )
        }
    }

    /** 首条消息才建线（懒建）；当前线已存在则直接复用 */
    private suspend fun ensureThread(firstMessageText: String): Long {
        _currentThreadId.value?.let { return it }
        val id = store.createThread(activeContext, firstMessageText)
        _currentThreadId.value = id
        return id
    }

    // 可见列表的原子更新（消息 id 全局唯一，按 id 定位即可，无需 threadId）

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

    private fun applyToVisible(messageId: Long, transform: (ChatMessage) -> ChatMessage) {
        _uiState.update { s ->
            s.copy(messages = s.messages.map { if (it.id == messageId) transform(it) else it })
        }
    }

    /** 流式增量与搜索事件只落在占位上：单次原子更新，无锁也不会与串行操作交错出错态 */
    private fun appendDelta(delta: String) =
        applyToVisible(PLACEHOLDER_ID) { it.copy(content = it.content + delta) }

    private fun applySearchEvent(event: SearchEvent) = applyToVisible(PLACEHOLDER_ID) { m ->
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
