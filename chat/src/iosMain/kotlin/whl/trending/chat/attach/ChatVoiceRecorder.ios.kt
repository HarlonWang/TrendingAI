package whl.trending.chat.attach

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.ExperimentalForeignApi
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import platform.AVFAudio.AVAudioApplication
import platform.AVFAudio.AVAudioApplicationRecordPermissionDenied
import platform.AVFAudio.AVAudioApplicationRecordPermissionGranted
import platform.AVFAudio.AVAudioRecorder
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryRecord
import platform.AVFAudio.AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation
import platform.AVFAudio.AVEncoderBitRateKey
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVSampleRateKey
import platform.AVFAudio.setActive
import platform.CoreAudioTypes.kAudioFormatMPEG4AAC
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSTimer
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import whl.trending.chat.core.epochMillis
import whl.trending.chat.core.logWarn

private const val TAG = "ChatVoiceRecorder"
private const val DIR = "chat_voice"

/**
 * iOS 实现：AVAudioRecorder 直出 AAC m4a，规格对齐 Android 侧（单声道 16kHz 32kbps）。
 * 权限走 AVAudioApplication（iOS 17+，部署目标 18.2），首次按下发起申请、再按一次才开始录。
 * 录音期间把 AVAudioSession 切到 record 类别，取件/取消后立即释放，不影响其他 app 的播放。
 */
@Composable
actual fun rememberChatVoiceRecorder(
    maxDurationMs: Int,
    minDurationMs: Int,
    onAutoStop: (VoiceRecording) -> Unit,
    onPermissionDenied: () -> Unit,
): ChatVoiceRecorder {
    val currentAutoStop by rememberUpdatedState(onAutoStop)
    val currentDenied by rememberUpdatedState(onPermissionDenied)
    return remember(maxDurationMs, minDurationMs) {
        IosVoiceRecorder(
            maxDurationMs = maxDurationMs,
            minDurationMs = minDurationMs,
            onAutoStop = { currentAutoStop(it) },
            onPermissionDenied = { currentDenied() },
        )
    }
}

@OptIn(ExperimentalForeignApi::class, ExperimentalUuidApi::class)
private class IosVoiceRecorder(
    private val maxDurationMs: Int,
    private val minDurationMs: Int,
    private val onAutoStop: (VoiceRecording) -> Unit,
    private val onPermissionDenied: () -> Unit,
) : ChatVoiceRecorder {

    private var recorder: AVAudioRecorder? = null
    private var path: String? = null
    private var startedAt = 0L
    private var autoStopTimer: NSTimer? = null

    override val isAvailable = true

    override fun start(): VoiceStart {
        if (recorder != null) return VoiceStart.FAILED
        when (AVAudioApplication.sharedInstance.recordPermission) {
            AVAudioApplicationRecordPermissionGranted -> Unit
            AVAudioApplicationRecordPermissionDenied -> {
                onPermissionDenied()
                return VoiceStart.PERMISSION_PENDING
            }
            else -> {
                AVAudioApplication.requestRecordPermissionWithCompletionHandler { granted ->
                    if (!granted) dispatch_async(dispatch_get_main_queue()) { onPermissionDenied() }
                }
                return VoiceStart.PERMISSION_PENDING
            }
        }

        val session = AVAudioSession.sharedInstance()
        if (!session.setCategory(AVAudioSessionCategoryRecord, error = null) || !session.setActive(true, error = null)) {
            logWarn(TAG, "audio session activation failed")
            return VoiceStart.FAILED
        }
        val dir = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true)
            .first() as String + "/" + DIR
        NSFileManager.defaultManager.createDirectoryAtPath(dir, true, null, null)
        val target = "$dir/${Uuid.random()}.m4a"
        val settings = mapOf<Any?, Any?>(
            AVFormatIDKey to kAudioFormatMPEG4AAC,
            AVSampleRateKey to 16_000.0,
            AVNumberOfChannelsKey to 1,
            AVEncoderBitRateKey to 32_000,
        )
        val rec = AVAudioRecorder(NSURL.fileURLWithPath(target), settings, null)
        if (!rec.prepareToRecord() || !rec.record()) {
            logWarn(TAG, "record start failed")
            rec.stop()
            deactivateSession()
            runCatching { FileSystem.SYSTEM.delete(target.toPath()) }
            return VoiceStart.FAILED
        }
        recorder = rec
        path = target
        startedAt = epochMillis()
        autoStopTimer = NSTimer.scheduledTimerWithTimeInterval(maxDurationMs / 1000.0, repeats = false) {
            stop()?.let(onAutoStop)
        }
        return VoiceStart.STARTED
    }

    override fun stop(): VoiceRecording? {
        val rec = recorder ?: return null
        val target = path
        // 取录音器自己计的秒数而非墙钟：音频会话激活到真正开录之间有延迟（模拟器可达数秒），
        // 墙钟从 start() 返回起算会把有效时长算短，与文件里的音频对不上
        val durationMs = (rec.currentTime * 1000).toLong().takeIf { it > 0 } ?: (epochMillis() - startedAt)
        release(rec)
        val size = target?.let { runCatching { FileSystem.SYSTEM.metadata(it.toPath()).size ?: 0L }.getOrDefault(0L) } ?: 0L
        if (target == null || durationMs < minDurationMs || size == 0L) {
            target?.let { runCatching { FileSystem.SYSTEM.delete(it.toPath()) } }
            return null
        }
        return VoiceRecording(target, durationMs)
    }

    override fun cancel() {
        val rec = recorder ?: return
        val target = path
        release(rec)
        target?.let { runCatching { FileSystem.SYSTEM.delete(it.toPath()) } }
    }

    override fun openPermissionSettings() {
        val url = NSURL(string = UIApplicationOpenSettingsURLString)
        UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any?>(), completionHandler = null)
    }

    private fun release(rec: AVAudioRecorder) {
        autoStopTimer?.invalidate()
        autoStopTimer = null
        recorder = null
        path = null
        rec.stop()
        deactivateSession()
    }

    private fun deactivateSession() {
        AVAudioSession.sharedInstance().setActive(
            false,
            withOptions = AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation,
            error = null,
        )
    }
}
