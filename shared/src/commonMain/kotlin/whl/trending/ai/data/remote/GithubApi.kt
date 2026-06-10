package whl.trending.ai.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

@Serializable
data class GithubFollowing(
    val login: String,
    val type: String = "User",
)

@Serializable
private data class GithubRepoDto(
    @SerialName("full_name") val fullName: String,
)

@Serializable
data class GithubUser(
    val login: String,
    val followers: Int = 0,
    val following: Int = 0,
    @SerialName("public_repos") val publicRepos: Int = 0,
)

@Serializable
data class GithubEventActor(
    val login: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

@Serializable
data class GithubEventRepo(
    val name: String,
)

@Serializable
data class GithubEventDto(
    val id: String,
    val type: String,
    val actor: GithubEventActor,
    val repo: GithubEventRepo,
    /** 各事件类型 payload 结构不同，保留原始 JSON 由 mapper 弹性提取 */
    val payload: JsonElement? = null,
    @SerialName("created_at") val createdAt: String,
)

/** GitHub REST 直连：feed 与计数。token 来自 GithubTokenProvider（Secret Vault 取回）。 */
open class GithubApi {
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

    private val baseHost = "https://api.github.com"

    open suspend fun fetchUser(githubToken: String): GithubUser {
        val response = client.get("$baseHost/user") {
            header(HttpHeaders.Authorization, "Bearer $githubToken")
            header(HttpHeaders.Accept, "application/vnd.github+json")
        }
        if (response.status.value !in 200..299) {
            throw ApiException(response.status.value, response.bodyAsText())
        }
        return response.body<GithubUser>()
    }

    open suspend fun fetchReceivedEvents(
        githubToken: String,
        login: String,
        page: Int,
        perPage: Int = 30,
    ): List<GithubEventDto> {
        val response = client.get("$baseHost/users/$login/received_events") {
            header(HttpHeaders.Authorization, "Bearer $githubToken")
            header(HttpHeaders.Accept, "application/vnd.github+json")
            parameter("per_page", perPage)
            parameter("page", page)
        }
        if (response.status.value !in 200..299) {
            throw ApiException(response.status.value, response.bodyAsText())
        }
        return response.body<List<GithubEventDto>>()
    }

    open suspend fun fetchFollowing(
        githubToken: String,
        page: Int,
        perPage: Int = 100,
    ): List<GithubFollowing> {
        val response = client.get("$baseHost/user/following") {
            header(HttpHeaders.Authorization, "Bearer $githubToken")
            header(HttpHeaders.Accept, "application/vnd.github+json")
            parameter("per_page", perPage)
            parameter("page", page)
        }
        if (response.status.value !in 200..299) {
            throw ApiException(response.status.value, response.bodyAsText())
        }
        return response.body<List<GithubFollowing>>()
    }

    open suspend fun fetchOwnRepos(githubToken: String): List<String> {
        val response = client.get("$baseHost/user/repos") {
            header(HttpHeaders.Authorization, "Bearer $githubToken")
            header(HttpHeaders.Accept, "application/vnd.github+json")
            parameter("per_page", 100)
            parameter("affiliation", "owner")
            parameter("sort", "pushed")
        }
        if (response.status.value !in 200..299) {
            throw ApiException(response.status.value, response.bodyAsText())
        }
        return response.body<List<GithubRepoDto>>().map { it.fullName }
    }

    open suspend fun fetchRepoEvents(
        githubToken: String,
        fullName: String,
        perPage: Int = 30,
    ): List<GithubEventDto> {
        val response = client.get("$baseHost/repos/$fullName/events") {
            header(HttpHeaders.Authorization, "Bearer $githubToken")
            header(HttpHeaders.Accept, "application/vnd.github+json")
            parameter("per_page", perPage)
        }
        if (response.status.value !in 200..299) {
            throw ApiException(response.status.value, response.bodyAsText())
        }
        return response.body<List<GithubEventDto>>()
    }
}
