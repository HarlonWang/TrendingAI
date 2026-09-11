package whl.trending.chat.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.isSuccess
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

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
            val bytes = runCatching {
                val response = shareClient.get(url)
                if (response.status.isSuccess()) response.bodyAsBytes() else null
            }.getOrNull()
            if (bytes != null && bytes.isNotEmpty()) shareBytes(bytes)
        }
    }
}

// 进程级单例（与 ChatApi 的 client 同款）：查看器每开一次都建 client 会漏引擎线程
private val shareClient by lazy {
    HttpClient { install(HttpTimeout) { requestTimeoutMillis = 30_000; connectTimeoutMillis = 10_000 } }
}

/** 一张 JPEG 的字节 → 系统分享面板。 */
@Composable
internal expect fun rememberShareImageBytes(): (ByteArray) -> Unit
