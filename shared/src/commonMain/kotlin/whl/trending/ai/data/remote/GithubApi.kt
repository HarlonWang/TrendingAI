package whl.trending.ai.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.put
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

/** followers / following 列表项：含头像与主页，供 Profile 下钻列表展示。 */
@Serializable
data class GithubUserSummary(
    val login: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    val type: String = "User",
)

/** repos 列表项：供 Profile 仓库下钻列表展示。 */
@Serializable
data class GithubRepoSummary(
    @SerialName("full_name") val fullName: String,
    val name: String,
    val description: String? = null,
    @SerialName("stargazers_count") val stars: Int = 0,
    val language: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    val fork: Boolean = false,
    @SerialName("pushed_at") val pushedAt: String? = null,
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

    /** 我的 followers 分页列表（含头像）。 */
    open suspend fun fetchFollowers(
        githubToken: String,
        page: Int,
        perPage: Int = 30,
    ): List<GithubUserSummary> {
        val response = client.get("$baseHost/user/followers") {
            header(HttpHeaders.Authorization, "Bearer $githubToken")
            header(HttpHeaders.Accept, "application/vnd.github+json")
            parameter("per_page", perPage)
            parameter("page", page)
        }
        if (response.status.value !in 200..299) {
            throw ApiException(response.status.value, response.bodyAsText())
        }
        return response.body<List<GithubUserSummary>>()
    }

    /** 我关注的 following 分页列表（含头像）。与 [fetchFollowing] 区分：后者仅供 feed 过滤取 login。 */
    open suspend fun fetchFollowingUsers(
        githubToken: String,
        page: Int,
        perPage: Int = 30,
    ): List<GithubUserSummary> {
        val response = client.get("$baseHost/user/following") {
            header(HttpHeaders.Authorization, "Bearer $githubToken")
            header(HttpHeaders.Accept, "application/vnd.github+json")
            parameter("per_page", perPage)
            parameter("page", page)
        }
        if (response.status.value !in 200..299) {
            throw ApiException(response.status.value, response.bodyAsText())
        }
        return response.body<List<GithubUserSummary>>()
    }

    /**
     * 我的自有公开仓库分页列表（含 fork），按最近 push 倒序。
     * 显式 visibility=public：与顶部 publicRepos 计数口径一致——否则默认会带回私有仓库，
     * 导致列表条数大于头部计数。
     */
    open suspend fun fetchReposPage(
        githubToken: String,
        page: Int,
        perPage: Int = 30,
    ): List<GithubRepoSummary> {
        val response = client.get("$baseHost/user/repos") {
            header(HttpHeaders.Authorization, "Bearer $githubToken")
            header(HttpHeaders.Accept, "application/vnd.github+json")
            parameter("per_page", perPage)
            parameter("page", page)
            parameter("affiliation", "owner")
            parameter("visibility", "public")
            parameter("sort", "pushed")
        }
        if (response.status.value !in 200..299) {
            throw ApiException(response.status.value, response.bodyAsText())
        }
        return response.body<List<GithubRepoSummary>>()
    }

    open suspend fun fetchOwnRepos(githubToken: String, perPage: Int = 100): List<String> {
        val response = client.get("$baseHost/user/repos") {
            header(HttpHeaders.Authorization, "Bearer $githubToken")
            header(HttpHeaders.Accept, "application/vnd.github+json")
            parameter("per_page", perPage)
            parameter("affiliation", "owner")
            parameter("sort", "pushed")
        }
        if (response.status.value !in 200..299) {
            throw ApiException(response.status.value, response.bodyAsText())
        }
        return response.body<List<GithubRepoDto>>().map { it.fullName }
    }

    /**
     * 查询当前用户是否已 star 某仓库：GET /user/starred/{owner}/{repo}。
     * 204=已 star，404=未 star。需 token 含 `public_repo` scope，否则 GitHub 同样以 404 兜底。
     */
    open suspend fun isStarred(githubToken: String, owner: String, repo: String): Boolean {
        val response = client.get("$baseHost/user/starred/$owner/$repo") {
            header(HttpHeaders.Authorization, "Bearer $githubToken")
            header(HttpHeaders.Accept, "application/vnd.github+json")
        }
        return when (response.status.value) {
            in 200..299 -> true
            404 -> false
            else -> throw ApiException(response.status.value, response.bodyAsText())
        }
    }

    /** 给仓库 star：PUT /user/starred/{owner}/{repo}，204 成功。空 body 由 Ktor 自动带 Content-Length: 0。 */
    open suspend fun starRepo(githubToken: String, owner: String, repo: String) {
        val response = client.put("$baseHost/user/starred/$owner/$repo") {
            header(HttpHeaders.Authorization, "Bearer $githubToken")
            header(HttpHeaders.Accept, "application/vnd.github+json")
        }
        if (response.status.value !in 200..299) {
            throw ApiException(response.status.value, response.bodyAsText())
        }
    }

    /** 取消 star：DELETE /user/starred/{owner}/{repo}，204 成功。 */
    open suspend fun unstarRepo(githubToken: String, owner: String, repo: String) {
        val response = client.delete("$baseHost/user/starred/$owner/$repo") {
            header(HttpHeaders.Authorization, "Bearer $githubToken")
            header(HttpHeaders.Accept, "application/vnd.github+json")
        }
        if (response.status.value !in 200..299) {
            throw ApiException(response.status.value, response.bodyAsText())
        }
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
