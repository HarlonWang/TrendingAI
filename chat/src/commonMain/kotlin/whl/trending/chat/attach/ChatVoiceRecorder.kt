package whl.trending.chat.attach

import androidx.compose.runtime.Composable

/** 一段录好的语音：本地 m4a 路径 + 时长。 */
data class VoiceRecording(val path: String, val durationMs: Long)

/** [ChatVoiceRecorder.start] 的结果：权限未授予时已发起申请（用户需再按一次）；FAILED 是录音器本身起不来。 */
enum class VoiceStart { STARTED, PERMISSION_PENDING, FAILED }

/**
 * 语音录入的平台缝：按住录音、松手取件。产出为 AAC m4a（单声道 16kHz 低码率），
 * 权限申请与被拒反馈由各平台在 [start] 内自行处理。
 */
interface ChatVoiceRecorder {
    /** 平台是否有录音能力；false 时 UI 不渲染麦克风。 */
    val isAvailable: Boolean

    fun start(): VoiceStart

    /** 结束并取件；不足最短时长或写文件失败返回 null（文件已清理）。 */
    fun stop(): VoiceRecording?

    /** 放弃本次录音并清理文件。 */
    fun cancel()

    /** 打开系统的应用权限设置页（权限被永久拒绝后的出口）。 */
    fun openPermissionSettings()
}

/**
 * @param maxDurationMs 触顶自动停止的上限
 * @param minDurationMs 短于此视为误触，[ChatVoiceRecorder.stop] 返回 null
 * @param onAutoStop 触顶自动停止时的取件回调（此时手指仍按着，UI 据此结束本次手势）
 * @param onPermissionDenied 权限申请被拒
 */
@Composable
expect fun rememberChatVoiceRecorder(
    maxDurationMs: Int,
    minDurationMs: Int,
    onAutoStop: (VoiceRecording) -> Unit,
    onPermissionDenied: () -> Unit,
): ChatVoiceRecorder
