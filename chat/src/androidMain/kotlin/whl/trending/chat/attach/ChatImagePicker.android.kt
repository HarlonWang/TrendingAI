package whl.trending.chat.attach

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import trendingai.chat.generated.resources.Res
import trendingai.chat.generated.resources.chat_image_processing_failed

/**
 * Android 实现：相册走 Photo Picker、拍照走 TakePicture + 专属 FileProvider，
 * 全程零运行时权限（Android 13+ 系统契约）；失败反馈用 Toast。
 */
@Composable
actual fun rememberChatImagePicker(
    maxImages: Int,
    remaining: () -> Int,
    onProcessingChange: (Int) -> Unit,
    onImageReady: (String) -> Unit,
): ChatImagePicker {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val failedText = stringResource(Res.string.chat_image_processing_failed)
    var captureTarget by remember { mutableStateOf<Pair<Uri, File>?>(null) }
    val currentRemaining by rememberUpdatedState(remaining)
    val currentProcessing by rememberUpdatedState(onProcessingChange)
    val currentReady by rememberUpdatedState(onImageReady)

    fun ingest(uri: Uri, deleteAfter: File? = null) {
        currentProcessing(+1)
        scope.launch {
            val path = ChatImages.ingest(context, uri)
            deleteAfter?.delete()
            currentProcessing(-1)
            if (path != null) {
                currentReady(path)
            } else {
                Toast.makeText(context, failedText, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val albumLauncher = rememberLauncherForActivityResult(
        // PickMultipleVisualMedia 要求 maxItems > 1，配置极端收紧到 1 时也不能让它抛
        ActivityResultContracts.PickMultipleVisualMedia(maxImages.coerceAtLeast(2)),
    ) { uris ->
        // 选择器允许选满上限，剩余名额不足时截断
        uris.take(currentRemaining().coerceAtLeast(0)).forEach { ingest(it) }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        val target = captureTarget
        captureTarget = null
        if (target != null) {
            if (success) {
                ingest(Uri.fromFile(target.second), deleteAfter = target.second)
            } else {
                target.second.delete()
            }
        }
    }

    return remember(context, scope) {
        object : ChatImagePicker {
            override val canCapture = true

            override fun pickFromAlbum() {
                albumLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            }

            override fun capture() {
                val target = ChatImages.newCaptureTarget(context)
                captureTarget = target
                // 极少数无相机应用的设备：launch 会抛 ActivityNotFoundException
                runCatching { cameraLauncher.launch(target.first) }
                    .onFailure {
                        captureTarget = null
                        Toast.makeText(context, failedText, Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }
}
