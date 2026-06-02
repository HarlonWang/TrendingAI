package whl.trending.chat.model

/**
 * 聊天页 UI 状态。
 *
 * @param messages   会话消息列表（内存级单会话）
 * @param input      输入框当前文本
 * @param isSending  是否正在等待助手回复（驱动「思考中」指示器与发送按钮禁用）
 */
data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val isSending: Boolean = false,
) {
    val canSend: Boolean get() = input.isNotBlank() && !isSending
}
