package whl.trending.ai.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import whl.trending.ai.auth.LOGTO_ENDPOINT

@Serializable
data class GithubTokenResponse(
    @SerialName("access_token") val accessToken: String,
)

/**
 * Logto Account API：从 Secret Vault 取回用户的第三方（GitHub）access token。
 * 凭据是用户自己的 Logto opaque access token（登录 scope 已含 identities）。
 */
open class LogtoAccountApi {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 15000
            connectTimeoutMillis = 15000
            socketTimeoutMillis = 15000
        }
    }

    /**
     * 取 GitHub access token。404=该用户未存 token（如撤销过授权），返回 null；
     * 其余非 2xx 抛 ApiException（401 含 token 过期，调用方决定重试/登出）。
     */
    open suspend fun fetchGithubToken(logtoAccessToken: String): String? {
        val response = client.get("$LOGTO_ENDPOINT/api/my-account/identities/github/access-token") {
            header(HttpHeaders.Authorization, "Bearer $logtoAccessToken")
        }
        if (response.status.value == 404) return null
        if (response.status.value !in 200..299) {
            throw ApiException(response.status.value, response.bodyAsText())
        }
        return response.body<GithubTokenResponse>().accessToken
    }
}
