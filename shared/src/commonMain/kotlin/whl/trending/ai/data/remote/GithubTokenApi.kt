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

@Serializable
data class GithubTokenResponse(
    @SerialName("access_token") val accessToken: String,
)

/**
 * 从自家后端取回用户的 GitHub access token。
 *
 * 取代 Logto Account API（`{endpoint}/api/my-account/identities/github/access-token`）——
 * Logto 退役后 Secret Vault 一并消失，token 改由后端加密存在 `app_users.gh_token_enc`，
 * 经本端点取回。**响应形状与 Logto 那版一致**（`{access_token}`），所以除了 URL 与
 * 鉴权用的 token 来源，调用方一行不用改。
 *
 * 凭据是 loginbase 的 access token（后端 requireAuth 的 loginbase 轨道）。
 */
open class GithubTokenApi {
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
     * 取 GitHub access token。404 = 该用户没存 token（纯邮箱账号、或撤销过授权），
     * 返回 null——调用方据此显示「关联 GitHub」引导而不是报错。
     * 其余非 2xx 抛 ApiException（401 含 token 过期，调用方决定重试/登出）。
     */
    open suspend fun fetchGithubToken(accessToken: String): String? {
        val response = client.get("$API_BASE/api/github/token") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        if (response.status.value == 404) return null
        if (response.status.value !in 200..299) {
            throw ApiException(response.status.value, response.bodyAsText())
        }
        return response.body<GithubTokenResponse>().accessToken
    }

    private companion object {
        const val API_BASE = "https://api.trendingai.cn"
    }
}
