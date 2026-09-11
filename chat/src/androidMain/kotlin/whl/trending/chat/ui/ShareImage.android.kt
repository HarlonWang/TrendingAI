package whl.trending.chat.ui

import android.content.Intent
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.util.UUID

@Composable
internal actual fun rememberShareImageUrl(): (String) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return { url ->
        scope.launch {
            val file = withContext(Dispatchers.IO) {
                runCatching {
                    // 与拍照/压缩产物同目录：FileProvider 路径配置与 24h 清理都已覆盖
                    val dir = File(context.cacheDir, "chat_images").apply { mkdirs() }
                    File(dir, "share_${UUID.randomUUID()}.jpg").also { f ->
                        URL(url).openStream().use { input -> f.outputStream().use { input.copyTo(it) } }
                    }
                }.onFailure { Log.w("ShareImage", "download failed", it) }.getOrNull()
            } ?: return@launch
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
