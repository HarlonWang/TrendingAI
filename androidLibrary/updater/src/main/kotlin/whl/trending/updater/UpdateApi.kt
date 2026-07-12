package whl.trending.updater

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import whl.trending.ai.update.WhatsNewInfo
import whl.trending.ai.update.parseWhatsNew

@Serializable
private data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val assets: List<GitHubAsset> = emptyList(),
)

@Serializable
private data class GitHubAsset(
    val name: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
)

data class LatestRelease(
    val tagName: String,
    /** release 附件里 whatsnew.json 的下载地址；该版未上传时为 null（回退为不带更新内容的弹窗） */
    val whatsNewAssetUrl: String?,
)

private val releaseJson = Json { ignoreUnknownKeys = true }

internal fun parseLatestRelease(raw: String): LatestRelease? =
    runCatching { releaseJson.decodeFromString<GitHubRelease>(raw) }.getOrNull()?.let { release ->
        LatestRelease(
            tagName = release.tagName,
            whatsNewAssetUrl = release.assets
                .firstOrNull { it.name == "whatsnew.json" }
                ?.browserDownloadUrl
                ?.takeIf { it.isNotBlank() },
        )
    }

/** asset 内容须非空且 version 与本次 release 的 tag 一致才采用，防止串版展示。 */
internal fun acceptWhatsNew(info: WhatsNewInfo?, tag: String): WhatsNewInfo? =
    info?.takeIf { !it.isEmpty && it.version == tag }

class UpdateApi {
    private val client = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 10_000
            connectTimeoutMillis = 10_000
        }
    }

    suspend fun fetchLatestRelease(): LatestRelease? = try {
        parseLatestRelease(
            client.get(
                "https://api.github.com/repos/HarlonWang/TrendingAI/releases/latest"
            ).bodyAsText()
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    suspend fun fetchWhatsNew(url: String): WhatsNewInfo? = try {
        parseWhatsNew(client.get(url).bodyAsText())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
