package whl.trending.chat.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.utils.io.readAvailable
import io.ktor.http.isSuccess
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okio.Buffer

/**
 * 把一张网络图片交给系统分享面板（保存到相册走系统面板，不另申请相册写权限）。
 * 下载在 common 层用 Ktor（有超时、随协程取消，连点只保留最后一次），平台层只管把字节交给面板；
 * 失败静默不弹。
 */
@Composable
internal fun rememberShareImageUrl(): (String) -> Unit {
    val scope = rememberCoroutineScope()
    val shareBytes = rememberShareImageBytes()
    val inFlight = remember { arrayOfNulls<Job>(1) }
    return { url ->
        inFlight[0]?.cancel()
        inFlight[0] = scope.launch {
            val bytes = runCatching { downloadBounded(url) }.getOrNull()
            if (bytes != null && bytes.isNotEmpty()) shareBytes(bytes)
        }
    }
}

/** 分享按钮对正文里任何 http 图片都亮，来源不可控：Content-Length 超限直接放弃，无长度声明时按块累计到上限即止。 */
private suspend fun downloadBounded(url: String): ByteArray? {
    val response = shareClient.get(url)
    if (!response.status.isSuccess()) return null
    val declared = response.contentLength()
    if (declared != null && declared > MAX_SHARE_IMAGE_BYTES) return null
    val channel = response.bodyAsChannel()
    val out = Buffer()
    val buf = ByteArray(64 * 1024)
    while (true) {
        val n = channel.readAvailable(buf, 0, buf.size)
        if (n <= 0) break
        if (out.size + n > MAX_SHARE_IMAGE_BYTES) return null
        out.write(buf, 0, n)
    }
    return out.readByteArray()
}

private const val MAX_SHARE_IMAGE_BYTES = 10 * 1024 * 1024

// 进程级单例（与 ChatApi 的 client 同款）：查看器每开一次都建 client 会漏引擎线程
private val shareClient by lazy {
    HttpClient { install(HttpTimeout) { requestTimeoutMillis = 30_000; connectTimeoutMillis = 10_000 } }
}

/** 一张 JPEG 的字节 → 系统分享面板。suspend：Android 要先落盘（IO 线程），面板本身在调用方的主线程起。 */
@Composable
internal expect fun rememberShareImageBytes(): suspend (ByteArray) -> Unit
