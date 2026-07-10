package whl.trending.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import whl.trending.ai.chat.ChatContext
import whl.trending.ai.core.platform.trackEvent
import whl.trending.ai.data.model.ChatModelOption
import whl.trending.ai.data.repository.ChatModelsProvider
import whl.trending.chat.engine.ChatEngine
import whl.trending.chat.engine.ChatException
import whl.trending.chat.model.ChatError
import whl.trending.chat.model.ChatErrorCategory
import whl.trending.chat.model.ChatMessage
import whl.trending.chat.model.ChatUiState
import whl.trending.chat.model.MessageKind
import whl.trending.chat.model.Role

/**
 * 聊天 ViewModel：内存级单会话，流式渲染（发送先追加空 assistant 占位，delta 到达增量更新）。
 * 通过 [engine] 注入实现 Demo / 正式切换。
 *
 * @param loadModels 模型目录拉取，注入点（便于测试替身）；默认走 [ChatModelsProvider] 的进程级缓存，
 *   避免每个会话都网络冷拉取导致选择器 chip 迟迟不出现（见冷首拉根因）。
 * @param track 埋点注入点（便于测试替身），默认 [trackEvent]。
 */
class ChatViewModel(
    private val engine: ChatEngine,
    private val context: ChatContext? = null,
    initialMessages: List<ChatMessage> = emptyList(),
    private val loadModels: suspend () -> List<ChatModelOption> = { ChatModelsProvider.get() },
    private val track: (String, Map<String, String>) -> Unit = { name, props -> trackEvent(name, props) },
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState(messages = initialMessages))
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    // 可选模型目录（驱动模型选择器）。拉取失败保持空列表 → 选择器隐藏、退回默认模型。
    private val _models = MutableStateFlow<List<ChatModelOption>>(emptyList())
    val models: StateFlow<List<ChatModelOption>> = _models.asStateFlow()

    init {
        viewModelScope.launch {
            _models.value = runCatching { loadModels() }.getOrDefault(emptyList())
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
        if (_uiState.value.isSending || context?.externalId == null) return
        track("detail_summary_generate", mapOf("from" to "chat"))
        val userMessage = ChatMessage(nextId(), Role.USER, promptText, kind = MessageKind.DETAIL_SUMMARY)
        _uiState.update { it.copy(messages = it.messages + userMessage, isSending = true) }
        launchRequest(MessageKind.DETAIL_SUMMARY)
    }

    private fun sendMessage(text: String, images: List<String>) {
        if (_uiState.value.isSending || (text.isBlank() && images.isEmpty())) return
        if (images.isNotEmpty()) {
            track("chat_send_with_images", mapOf("image_count" to images.size.toString()))
        }
        val userMessage = ChatMessage(nextId(), Role.USER, text, images = images)
        _uiState.update {
            it.copy(messages = it.messages + userMessage, isSending = true)
        }
        launchRequest(MessageKind.CHAT)
    }

    companion object {
        /** 单条消息图片数上限，与服务端及 [whl.trending.chat.engine.ChatWire] 对齐 */
        const val MAX_IMAGES_PER_MESSAGE = 4
    }

    /** 对可重试的失败消息重试：移除该错误条，按消息 [MessageKind] 路由回对应管线。
     *  quota_device / login_required 例外放行：触顶或登录闸之后完成登录，重发即可继续。 */
    fun retry(message: ChatMessage) {
        val error = message.error ?: return
        val passthrough = error.code == ChatError.CODE_QUOTA_DEVICE ||
            error.code == ChatError.CODE_LOGIN_REQUIRED
        if (!error.category.retryable && !passthrough) return
        _uiState.update {
            it.copy(messages = it.messages.filterNot { m -> m.id == message.id }, isSending = true)
        }
        launchRequest(message.kind)
    }

    /**
     * 统一请求路径：先追加空 assistant 占位消息，delta 到达时增量更新其 content；
     * 成功以全文定稿，失败清空已渲染部分（整条重试）并挂上分类错误。
     */
    private fun launchRequest(kind: MessageKind) = viewModelScope.launch {
        val placeholderId = nextId()
        _uiState.update {
            it.copy(messages = it.messages + ChatMessage(placeholderId, Role.ASSISTANT, "", kind = kind))
        }
        val result = runCatching {
            when (kind) {
                MessageKind.CHAT -> {
                    val history = _uiState.value.messages.filterNot { it.id == placeholderId }
                    engine.send(history, context) { delta -> appendDelta(placeholderId, delta) }
                }
                MessageKind.DETAIL_SUMMARY -> {
                    val detail = engine.sendDetailSummary(requireNotNull(context)) { delta ->
                        appendDelta(placeholderId, delta)
                    }
                    if (detail.cached) track("detail_summary_cache_hit", mapOf("from" to "chat"))
                    detail.content
                }
            }
        }
        result.fold(
            onSuccess = { full ->
                _uiState.update { st ->
                    st.copy(
                        messages = st.messages.map { m ->
                            if (m.id == placeholderId) m.copy(content = full) else m
                        },
                        isSending = false,
                    )
                }
            },
            onFailure = { e ->
                val error = (e as? ChatException)?.error
                    ?: ChatError(ChatErrorCategory.UNKNOWN, detail = e.toString())
                trackFailure(kind, error)
                _uiState.update { st ->
                    st.copy(
                        // 已渲染部分丢弃，整条重试（中途断流语义）
                        messages = st.messages.map { m ->
                            if (m.id == placeholderId) m.copy(content = "", error = error) else m
                        },
                        isSending = false,
                    )
                }
            },
        )
    }

    private fun appendDelta(messageId: Long, delta: String) {
        _uiState.update { st ->
            st.copy(
                messages = st.messages.map { m ->
                    if (m.id == messageId) m.copy(content = m.content + delta) else m
                },
            )
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
}
