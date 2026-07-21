package whl.trending.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import whl.trending.ai.chat.ChatContext
import whl.trending.ai.core.platform.trackEvent
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.data.model.ChatModelOption
import whl.trending.ai.data.repository.ChatModelsProvider
import whl.trending.chat.engine.ChatEngine
import whl.trending.chat.engine.ChatException
import whl.trending.chat.model.ChatError
import whl.trending.chat.model.ChatErrorCategory
import whl.trending.chat.model.ChatMessage
import whl.trending.chat.model.ChatMode
import whl.trending.chat.model.ChatUiState
import whl.trending.chat.model.MessageKind
import whl.trending.chat.model.Role
import whl.trending.chat.model.SearchEvent
import whl.trending.chat.model.SourceRef
import whl.trending.chat.store.ChatStore

/** 抽屉里的一条会话概要 */
data class ThreadSummary(val id: Long, val title: String, val updatedAt: Long)

/**
 * 聊天 ViewModel：单实例 + currentThreadId 状态（P1 起，多会话由 [ChatStore] 支撑）。
 * 流式渲染不变（发送先追加空 assistant 占位，delta 到达增量更新）；落库为终局一次写。
 *
 * 会话切换语义：流按 threadId 分键（[streams]/[messagesByThread]）——切走后后台继续
 * 完成并落库（服务端已在计费，取消才是浪费），切回可见全文；uiState 只镜像当前线。
 *
 * @param store 持久化层；null = 纯内存模式（Demo/预览与旧行为完全一致）
 * @param selectedModelId 应答模型记录用（展示「哪个模型答的」）；默认读全局设置的用户选择
 */
class ChatViewModel(
    private val engine: ChatEngine,
    initialContext: ChatContext? = null,
    initialMessages: List<ChatMessage> = emptyList(),
    private val store: ChatStore? = null,
    private val loadModels: suspend () -> List<ChatModelOption> = { ChatModelsProvider.get() },
    private val track: (String, Map<String, String>) -> Unit = { name, props -> trackEvent(name, props) },
    private val selectedModelId: () -> String? =
        { runCatching { globalSettingsManager.currentSelectedChatModel() }.getOrNull() },
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState(messages = initialMessages))
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    // 可选模型目录（驱动模型选择器）。拉取失败保持空列表 → 选择器隐藏、退回默认模型。
    private val _models = MutableStateFlow<List<ChatModelOption>>(emptyList())
    val models: StateFlow<List<ChatModelOption>> = _models.asStateFlow()

    /** 会话列表（抽屉数据源）；纯内存模式恒为空 */
    val threads: StateFlow<List<ThreadSummary>> =
        (store?.threads()?.map { list -> list.map { ThreadSummary(it.id, it.title, it.updatedAt) } }
            ?: flowOf(emptyList()))
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _currentThreadId = MutableStateFlow<Long?>(null)
    val currentThreadId: StateFlow<Long?> = _currentThreadId.asStateFlow()

    /** 会话能力模式（P2：联网搜索）。粘滞语义：开启后对后续每条消息生效，直到手动关闭 */
    private val _chatMode = MutableStateFlow<ChatMode>(ChatMode.Normal)
    val chatMode: StateFlow<ChatMode> = _chatMode.asStateFlow()

    /** EchoFlow 单选互斥范式：再点同项回 Normal，点别项自动顶掉 */
    fun toggleWebSearch() {
        _chatMode.value = if (_chatMode.value == ChatMode.WebSearch) ChatMode.Normal else ChatMode.WebSearch
    }

    /** 当前会话的 ChatContext（解读 chip / 服务端 context 注入）；随切换/恢复而变 */
    private var activeContext: ChatContext? = initialContext

    // 每线消息缓冲（真相源，uiState.messages 只是当前线的镜像）；null 键 = 尚未落库的新会话
    private val messagesByThread = mutableMapOf<Long?, List<ChatMessage>>(null to initialMessages)

    // 每线在途流（切走后后台继续）
    private val streams = mutableMapOf<Long?, Job>()

    init {
        viewModelScope.launch {
            _models.value = runCatching { loadModels() }.getOrDefault(emptyList())
        }
    }

    /**
     * 入口进入（Screen 每个新入口调用一次）：恢复该入口最近会话（跨进程延续原
     * sessionKey 的「再次进入恢复」体验）；无历史则以该 context 呈现空会话态。
     * 纯内存模式只切 context，不动 initialMessages。
     */
    fun enterEntry(context: ChatContext?) {
        viewModelScope.launch {
            activeContext = context
            val s = store ?: return@launch
            val id = s.resolveLatestThread(context)
            if (id != null) {
                openThread(id, fallbackContext = context)
            } else {
                _currentThreadId.value = null
                messagesByThread[null] = emptyList()
                _uiState.update {
                    it.copy(messages = emptyList(), isSending = false, pendingImages = emptyList())
                }
            }
        }
    }

    private var idSeq = initialMessages.maxOfOrNull { it.id } ?: 0L
    private fun nextId(): Long = ++idSeq

    fun updateInput(text: String) {
        _uiState.update { it.copy(input = text) }
    }

    /** 追加一张已压缩好的待发图片（本地缓存路径），超过单条上限时忽略。 */
    fun addPendingImage(path: String) {
        _uiState.update {
            if (it.pendingImages.size >= MAX_IMAGES_PER_MESSAGE || path in it.pendingImages) it
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
        sendMessage(text, images)
    }

    /** 发送一段指定文本（如快捷按钮的预设问题），不依赖输入框；发送中或空白则忽略。 */
    fun sendText(text: String) = sendMessage(text, emptyList())

    /**
     * 「一键详细解读」：插入一条 user 消息（chip 文案），走 detail 管线流式生成解读。
     * 可见性由 [DetailSummaryPolicy] 保证（GitHub 条目 + README 达标 + 尚无成功解读）。
     */
    fun sendDetailSummary(promptText: String) {
        val context = activeContext
        if (_uiState.value.isSending || context?.externalId == null) return
        track("detail_summary_generate", mapOf("from" to "chat"))
        _uiState.update { it.copy(isSending = true) }
        viewModelScope.launch {
            val threadId = ensureThreadForSend(promptText)
            val userMessage = ChatMessage(nextId(), Role.USER, promptText, kind = MessageKind.DETAIL_SUMMARY)
            appendAndPersistUser(threadId, userMessage)
            launchRequest(threadId, MessageKind.DETAIL_SUMMARY, context)
        }
    }

    private fun sendMessage(text: String, images: List<String>) {
        if (_uiState.value.isSending || (text.isBlank() && images.isEmpty())) return
        if (images.isNotEmpty()) {
            track("chat_send_with_images", mapOf("image_count" to images.size.toString()))
        }
        _uiState.update { it.copy(isSending = true) }
        viewModelScope.launch {
            val threadId = ensureThreadForSend(text)
            val userMessage = ChatMessage(nextId(), Role.USER, text, images = images)
            appendAndPersistUser(threadId, userMessage)
            launchRequest(threadId, MessageKind.CHAT, activeContext)
        }
    }

    /** 对可重试的失败消息重试：移除该错误条，按消息 [MessageKind] 路由回对应管线。
     *  quota_device / login_required 例外放行：触顶或登录闸之后完成登录，重发即可继续。 */
    fun retry(message: ChatMessage) {
        val error = message.error ?: return
        val passthrough = error.code == ChatError.CODE_QUOTA_DEVICE ||
            error.code == ChatError.CODE_LOGIN_REQUIRED
        if (!error.category.retryable && !passthrough) return
        val threadId = _currentThreadId.value
        updateThreadMessages(threadId) { list -> list.filterNot { it.id == message.id } }
        _uiState.update { it.copy(isSending = true) }
        launchRequest(threadId, message.kind, activeContext)
    }

    // ---- 会话管理（抽屉） ----

    fun switchThread(id: Long) {
        if (id == _currentThreadId.value) return
        viewModelScope.launch { openThread(id) }
    }

    /** 抽屉「新会话」：总是全新开始（通用入口），不复用任何历史 */
    fun startNewThread() {
        _currentThreadId.value = null
        activeContext = null
        messagesByThread[null] = emptyList()
        _uiState.update {
            it.copy(messages = emptyList(), isSending = false, input = "", pendingImages = emptyList())
        }
    }

    fun renameThread(id: Long, title: String) {
        viewModelScope.launch { store?.renameThread(id, title) }
    }

    fun deleteThread(id: Long) {
        viewModelScope.launch {
            // 在途流一并取消：线程行将消失，终局落库会撞外键
            streams.remove(id)?.cancel()
            messagesByThread.remove(id)
            store?.deleteThread(id)
            if (_currentThreadId.value == id) startNewThread()
        }
    }

    // ---- 内部 ----

    private suspend fun openThread(id: Long, fallbackContext: ChatContext? = null) {
        val s = store ?: return
        val messages = messagesByThread[id] ?: s.loadMessages(id).also { messagesByThread[id] = it }
        idSeq = maxOf(idSeq, messages.maxOfOrNull { it.id } ?: 0L)
        _currentThreadId.value = id
        activeContext = s.contextOf(id) ?: fallbackContext
        _uiState.update {
            it.copy(
                messages = messages,
                isSending = streams[id]?.isActive == true,
                pendingImages = emptyList(),
            )
        }
    }

    /** 首条消息才建线（懒建）；当前线已存在则直接复用。内存模式恒为 null 键。 */
    private suspend fun ensureThreadForSend(firstMessageText: String): Long? {
        val s = store ?: return _currentThreadId.value
        _currentThreadId.value?.let { return it }
        val id = s.createThread(activeContext, firstMessageText)
        // 未落库缓冲迁移到新线名下
        messagesByThread[id] = messagesByThread.remove(null) ?: emptyList()
        _currentThreadId.value = id
        return id
    }

    private suspend fun appendAndPersistUser(threadId: Long?, message: ChatMessage) {
        updateThreadMessages(threadId) { it + message }
        if (store != null && threadId != null) {
            val persisted = store.persistUserMessage(threadId, message)
            if (persisted.images != message.images) {
                // 图片已迁入 filesDir：回写新路径（cache 路径随时可能被系统清理）
                updateThreadMessages(threadId) { list ->
                    list.map { if (it.id == message.id) it.copy(images = persisted.images) else it }
                }
            }
        }
    }

    /**
     * 统一请求路径：先追加空 assistant 占位消息，delta 到达时增量更新其 content；
     * 成功以全文定稿并终局落库，失败清空已渲染部分（整条重试）并挂上分类错误（不落库）。
     */
    private fun launchRequest(threadId: Long?, kind: MessageKind, context: ChatContext?) {
        val placeholderId = nextId()
        updateThreadMessages(threadId) { it + ChatMessage(placeholderId, Role.ASSISTANT, "", kind = kind) }
        val job = viewModelScope.launch {
            val result = runCatching {
                when (kind) {
                    MessageKind.CHAT -> {
                        val history = (messagesByThread[threadId] ?: emptyList())
                            .filterNot { it.id == placeholderId }
                        val useSearch = _chatMode.value == ChatMode.WebSearch
                        engine.send(
                            history,
                            context,
                            onDelta = { delta -> appendDelta(threadId, placeholderId, delta) },
                            search = useSearch,
                            onSearch = { event -> applySearchEvent(threadId, placeholderId, event) },
                        )
                    }
                    MessageKind.DETAIL_SUMMARY -> {
                        val detail = engine.sendDetailSummary(requireNotNull(context)) { delta ->
                            appendDelta(threadId, placeholderId, delta)
                        }
                        if (detail.cached) track("detail_summary_cache_hit", mapOf("from" to "chat"))
                        detail.content
                    }
                }
            }
            result.fold(
                onSuccess = { full ->
                    var sources: List<SourceRef> = emptyList()
                    updateThreadMessages(threadId) { list ->
                        list.map {
                            if (it.id == placeholderId) {
                                sources = it.sources
                                it.copy(content = full, searching = false)
                            } else it
                        }
                    }
                    if (store != null && threadId != null) {
                        store.persistAssistantMessage(threadId, full, kind, selectedModelId(), sources)
                    }
                },
                onFailure = { e ->
                    val error = (e as? ChatException)?.error
                        ?: ChatError(ChatErrorCategory.UNKNOWN, detail = e.toString())
                    trackFailure(kind, error)
                    updateThreadMessages(threadId) { list ->
                        // 已渲染部分丢弃，整条重试（中途断流语义）
                        list.map { if (it.id == placeholderId) it.copy(content = "", error = error, searching = false) else it }
                    }
                },
            )
            streams.remove(threadId)
            if (threadId == _currentThreadId.value) {
                _uiState.update { it.copy(isSending = false) }
            }
        }
        streams[threadId] = job
    }

    /** 搜索事件落到占位消息：searching 瞬态 + sources 累积（服务端已按 url 去重，客户端再防御一层） */
    private fun applySearchEvent(threadId: Long?, messageId: Long, event: SearchEvent) {
        updateThreadMessages(threadId) { list ->
            list.map { m ->
                if (m.id != messageId) m
                else when (event) {
                    is SearchEvent.Started -> m.copy(searching = true)
                    is SearchEvent.Done -> m.copy(searching = false)
                    is SearchEvent.Source ->
                        m.copy(sources = (m.sources + SourceRef(event.title, event.url)).distinctBy { it.url })
                }
            }
        }
    }

    private fun appendDelta(threadId: Long?, messageId: Long, delta: String) {
        updateThreadMessages(threadId) { list ->
            list.map { if (it.id == messageId) it.copy(content = it.content + delta) else it }
        }
    }

    /** 所有消息变更的唯一入口：写缓冲，且仅当该线是当前线时镜像进 uiState */
    private fun updateThreadMessages(threadId: Long?, transform: (List<ChatMessage>) -> List<ChatMessage>) {
        val updated = transform(messagesByThread[threadId] ?: emptyList())
        messagesByThread[threadId] = updated
        if (threadId == _currentThreadId.value) {
            _uiState.update { it.copy(messages = updated) }
        }
    }

    private fun trackFailure(kind: MessageKind, error: ChatError) {
        when {
            // 付费意愿漏斗第一级：个人配额触顶（在 VM 记一次，避免 UI 重组重复上报）
            error.code == ChatError.CODE_QUOTA_DEVICE -> {
                val event = if (kind == MessageKind.DETAIL_SUMMARY) "detail_summary_quota_hit" else "chat_quota_hit"
                track(event, mapOf("tier" to (error.tier ?: ChatError.TIER_ANONYMOUS)))
            }
            // 登录转化关键信号：匿名点了未缓存条目的解读
            error.code == ChatError.CODE_LOGIN_REQUIRED && kind == MessageKind.DETAIL_SUMMARY -> {
                track("detail_summary_login_required", mapOf("from" to "chat"))
            }
        }
    }

    companion object {
        /** 单条消息图片数上限，与服务端及 [whl.trending.chat.engine.ChatWire] 对齐 */
        const val MAX_IMAGES_PER_MESSAGE = 4
    }
}
