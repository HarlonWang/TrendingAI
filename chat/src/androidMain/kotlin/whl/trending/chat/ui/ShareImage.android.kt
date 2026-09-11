package whl.trending.chat.ui

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@Composable
internal actual fun rememberShareImageBytes(): suspend (ByteArray) -> Unit {
    val context = LocalContext.current
    return { bytes ->
        val file = withContext(Dispatchers.IO) {
            // 与拍照/压缩产物同目录：FileProvider 路径配置与 24h 清理都已覆盖
            val dir = File(context.cacheDir, "chat_images").apply { mkdirs() }
            File(dir, "share_${UUID.randomUUID()}.jpg").takeIf { f ->
                runCatching { f.writeBytes(bytes) }.onFailure { f.delete() }.isSuccess
            }
        }
        if (file != null) {
            val uri = FileProvider.getUriForFile(context, context.packageName + ".chat.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, null).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        }
    }
}
