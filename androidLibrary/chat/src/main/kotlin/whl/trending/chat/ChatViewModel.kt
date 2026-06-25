package whl.trending.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import whl.trending.ai.chat.ChatContext
import whl.trending.chat.engine.ChatEngine
import whl.trending.chat.engine.ChatException
import whl.trending.chat.model.ChatError
import whl.trending.chat.model.ChatErrorCategory
import whl.trending.chat.model.ChatMessage
import whl.trending.chat.model.ChatUiState
import whl.trending.chat.model.Role

/**
 * 聊天 ViewModel：内存级单会话。通过 [engine] 注入实现 Demo / 正式切换。
 */
class ChatViewModel(
    private val engine: ChatEngine,
    private val context: ChatContext? = null,
    initialMessages: List<ChatMessage> = emptyList(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState(messages = initialMessages))
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var idSeq = initialMessages.maxOfOrNull { it.id } ?: 0L
    private fun nextId(): Long = ++idSeq

    fun updateInput(text: String) {
        _uiState.update { it.copy(input = text) }
    }

    fun send() {
        val state = _uiState.value
        if (!state.canSend) return
        val text = state.input.trim()
        _uiState.update { it.copy(input = "") }
        sendText(text)
    }

    /** 发送一段指定文本（如快捷按钮的预设问题），不依赖输入框；发送中或空白则忽略。 */
    fun sendText(text: String) {
        if (_uiState.value.isSending || text.isBlank()) return
        val userMessage = ChatMessage(nextId(), Role.USER, text)
        _uiState.update {
            it.copy(messages = it.messages + userMessage, isSending = true)
        }
        request()
    }

    /** 对可重试的失败消息重试：移除该错误条，按当前历史重新请求。 */
    fun retry(message: ChatMessage) {
        if (message.error?.category?.retryable != true) return
        _uiState.update {
            it.copy(messages = it.messages.filterNot { m -> m.id == message.id }, isSending = true)
        }
        request()
    }

    private fun request() = viewModelScope.launch {
        // 先插入一条空的流式占位 assistant 消息，随增量逐字刷新
        val streamId = nextId()
        _uiState.update {
            it.copy(messages = it.messages + ChatMessage(streamId, Role.ASSISTANT, "", isStreaming = true))
        }
        try {
            engine.send(historyForRequest(streamId), context).collect { delta ->
                _uiState.update { state ->
                    state.copy(messages = state.messages.map { m ->
                        if (m.id == streamId) m.copy(content = m.content + delta) else m
                    })
                }
            }
            // 正常收尾：去掉流式标记
            _uiState.update { state ->
                state.copy(
                    messages = state.messages.map { m ->
                        if (m.id == streamId) m.copy(isStreaming = false) else m
                    },
                    isSending = false,
                )
            }
        } catch (e: CancellationException) {
            throw e // 不吞协程取消（用户退出/停止）
        } catch (e: Throwable) {
            val error = (e as? ChatException)?.error
                ?: ChatError(ChatErrorCategory.UNKNOWN, detail = e.toString())
            // 占位消息替换为错误条（清空已累积内容，交给错误条展示 + 重试）
            _uiState.update { state ->
                state.copy(
                    messages = state.messages.map { m ->
                        if (m.id == streamId) m.copy(content = "", error = error, isStreaming = false) else m
                    },
                    isSending = false,
                )
            }
        }
    }

    /** 请求时携带的历史：剔除当前流式占位消息（它尚无内容，不应进 prompt）。 */
    private fun historyForRequest(streamId: Long): List<ChatMessage> =
        _uiState.value.messages.filterNot { it.id == streamId }
}
