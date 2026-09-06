package whl.trending.chat.model

/**
 * 聊天页 UI 状态。
 *
 * @param messages      会话消息列表（内存级单会话）
 * @param input         输入框当前文本
 * @param pendingImages 待发送图片（压缩后的本地缓存文件路径，随下一条消息发出）
 * @param isSending     是否正在等待助手回复（驱动「思考中」指示器与发送按钮禁用）
 * @param isTranscribing 语音录入转写在途（松手到文本进入对话之间的等待）
 */
data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val pendingImages: List<String> = emptyList(),
    val isSending: Boolean = false,
    val isTranscribing: Boolean = false,
) {
    /** 发送位被占用（回复在途或转写在途）：所有发送入口按此禁用，真正的门禁在 ViewModel */
    val isBusy: Boolean get() = isSending || isTranscribing

    val canSend: Boolean get() = (input.isNotBlank() || pendingImages.isNotEmpty()) && !isBusy
}
