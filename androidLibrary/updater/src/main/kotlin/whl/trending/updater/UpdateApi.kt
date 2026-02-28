package whl.trending.updater

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class GitHubRelease(
    @SerialName("tag_name") val tagName: String
)

class UpdateApi {
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 10_000
            connectTimeoutMillis = 10_000
        }
    }

    suspend fun fetchLatestVersion(): String? = try {
        client.get(
            "https://api.github.com/repos/HarlonWang/Trending/releases/latest"
        ).body<GitHubRelease>().tagName
    } catch (e: Exception) {
        null
    }
}
