package whl.trending.chat.attach

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import java.io.File
import java.util.UUID
import whl.trending.chat.core.logWarn

private const val TAG = "ChatVoiceRecorder"
private const val DIR = "chat_voice"

/**
 * Android 实现：MediaRecorder 直出 AAC m4a（单声道 16kHz 32kbps，60s 约 240KB）。
 * 权限走运行时申请；被拒后由 UI 引导去系统设置。
 */
@Composable
actual fun rememberChatVoiceRecorder(
    maxDurationMs: Int,
    minDurationMs: Int,
    onAutoStop: (VoiceRecording) -> Unit,
    onPermissionDenied: () -> Unit,
): ChatVoiceRecorder {
    val context = LocalContext.current
    val currentAutoStop by rememberUpdatedState(onAutoStop)
    val currentDenied by rememberUpdatedState(onPermissionDenied)
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (!granted) currentDenied() }

    return remember(context, maxDurationMs, minDurationMs) {
        AndroidVoiceRecorder(
            context = context,
            maxDurationMs = maxDurationMs,
            minDurationMs = minDurationMs,
            requestPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
            onAutoStop = { currentAutoStop(it) },
        )
    }
}

private class AndroidVoiceRecorder(
    private val context: Context,
    private val maxDurationMs: Int,
    private val minDurationMs: Int,
    private val requestPermission: () -> Unit,
    private val onAutoStop: (VoiceRecording) -> Unit,
) : ChatVoiceRecorder {

    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    override val isAvailable: Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)

    override fun start(): Boolean {
        if (recorder != null) return false
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestPermission()
            return false
        }
        val target = File(File(context.cacheDir, DIR).apply { mkdirs() }, "${UUID.randomUUID()}.m4a")
        @Suppress("DEPRECATION")
        val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
        try {
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setAudioChannels(1)
            mr.setAudioSamplingRate(16_000)
            mr.setAudioEncodingBitRate(32_000)
            mr.setMaxDuration(maxDurationMs)
            mr.setOutputFile(target.absolutePath)
            mr.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                    stop()?.let(onAutoStop)
                }
            }
            mr.prepare()
            mr.start()
        } catch (e: Exception) {
            logWarn(TAG, "start failed", e)
            mr.release()
            target.delete()
            return false
        }
        recorder = mr
        file = target
        startedAt = SystemClock.elapsedRealtime()
        return true
    }

    override fun stop(): VoiceRecording? {
        val mr = recorder ?: return null
        val target = file
        val durationMs = SystemClock.elapsedRealtime() - startedAt
        recorder = null
        file = null
        // 过短时 stop() 会因无有效数据抛 RuntimeException，与误触同一处理
        val ok = runCatching { mr.stop() }.isSuccess
        mr.release()
        if (!ok || target == null || durationMs < minDurationMs || target.length() == 0L) {
            target?.delete()
            return null
        }
        return VoiceRecording(target.absolutePath, durationMs)
    }

    override fun cancel() {
        val mr = recorder ?: return
        recorder = null
        runCatching { mr.stop() }
        mr.release()
        file?.delete()
        file = null
    }

    override fun openPermissionSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure { logWarn(TAG, "open settings failed", it) }
    }
}
