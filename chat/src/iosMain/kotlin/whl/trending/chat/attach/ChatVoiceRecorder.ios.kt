package whl.trending.chat.attach

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** iOS 录音尚未实现：isAvailable=false，麦克风入口不渲染。 */
@Composable
actual fun rememberChatVoiceRecorder(
    maxDurationMs: Int,
    minDurationMs: Int,
    onAutoStop: (VoiceRecording) -> Unit,
    onPermissionDenied: () -> Unit,
): ChatVoiceRecorder = remember {
    object : ChatVoiceRecorder {
        override val isAvailable = false
        override fun start() = false
        override fun stop(): VoiceRecording? = null
        override fun cancel() {}
        override fun openPermissionSettings() {}
    }
}
