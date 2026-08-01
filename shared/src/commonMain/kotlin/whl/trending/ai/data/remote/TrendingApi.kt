package whl.trending.ai.data.remote

import whl.trending.ai.core.platform.getUserAgent
import whl.trending.ai.data.model.AppConfigResponse
import whl.trending.ai.data.model.FavoriteItem
import whl.trending.ai.data.model.FavoritesResponse
import whl.trending.ai.data.model.FeedResponse
import whl.trending.ai.data.model.ChatModelsResponse
import whl.trending.ai.data.model.MeResponse
import whl.trending.ai.data.model.ProRefreshResponse
import whl.trending.ai.data.model.PicksResponse
import whl.trending.ai.data.model.QuotaResponse
import whl.trending.ai.data.model.ReadmeResponse
import whl.trending.ai.data.model.SubscribeResponse
import whl.trending.ai.data.model.TrendingResponse

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ApiException(val statusCode: Int, message: String) : Exception(message)

open class TrendingApi {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 15000
            connectTimeoutMillis = 15000
            socketTimeoutMillis = 15000
        }
        install(DefaultRequest) {
            header(HttpHeaders.UserAgent, getUserAgent())
        }
    }

    private val baseHost = "https://api.trendingai.cn"

    open suspend fun fetchTrending(
        period: String,
        language: String,
        summaryLang: String,
        date: String? = null,
        batch: String? = null
    ): TrendingResponse {
        val response = client.get("$baseHost/api/trending") {
            parameter("since", period.lowercase())
            parameter("lang", language.lowercase())
            parameter("summary_lang", summaryLang)
            if (!date.isNullOrBlank()) {
                parameter("date", date)
            }
            if (!batch.isNullOrBlank()) {
                parameter("batch", batch)
            }
        }
        return response.body<TrendingResponse>()
    }

    open suspend fun fetchFeed(source: String, summaryLang: String = "zh"): FeedResponse {
        val response = client.get("$baseHost/api/feed") {
            parameter("source", source)
            parameter("summary_lang", summaryLang)
        }
        return response.body<FeedResponse>()
    }

    open suspend fun fetchPicks(summaryLang: String = "zh"): PicksResponse {
        val response = client.get("$baseHost/api/picks") {
            parameter("summary_lang", summaryLang)
        }
        return response.body<PicksResponse>()
    }

    open suspend fun fetchReadme(owner: String, repo: String): ReadmeResponse {
        val response = client.get("$baseHost/api/readme") {
            parameter("owner", owner)
            parameter("repo", repo)
        }
        return response.body<ReadmeResponse>()
    }

    open suspend fun submitFeedback(content: String, email: String?): Result<Unit> {
        return try {
            val response = client.post("$baseHost/api/feedback") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("content", content)
                    if (!email.isNullOrBlank()) put("email", email)
                })
            }
            if (response.status.value in 200..299) {
                Result.success(Unit)
            } else {
                val body = response.bodyAsText()
                Result.failure(ApiException(response.status.value, body))
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }

    open suspend fun submitSubscribe(
        email: String,
        source: String,
        lang: String,
    ): Result<SubscribeResponse> {
        return try {
            val response = client.post("$baseHost/api/subscribe") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("email", email)
                    put("source", source)
                    put("lang", lang)
                })
            }
            if (response.status.value in 200..299) {
                Result.success(response.body<SubscribeResponse>())
            } else {
                Result.failure(ApiException(response.status.value, response.bodyAsText()))
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }

    /**
     * [fresh] = true 时带 `?fresh=1`：让服务端绕过 userinfo claims 的 10 分钟缓存重新拉取。
     * 仅用于刚在账户中心关联身份后——否则读到的仍是关联前的 identities，UI 会以为没绑上。
     */
    open suspend fun fetchMe(accessToken: String, fresh: Boolean = false): MeResponse {
        val response = client.get("$baseHost/api/me${if (fresh) "?fresh=1" else ""}") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        if (response.status.value !in 200..299) {
            throw ApiException(response.status.value, response.bodyAsText())
        }
        return response.body<MeResponse>()
    }

    /**
     * credits 余额查询（账户页配额卡）。X-Install-Id 必传（匿名记账主体）；
     * 已登录再带 Bearer——服务端按 user 主体与档位返回。响应服务端禁缓存，每次都是实时余额。
     */
    open suspend fun fetchQuota(installId: String, accessToken: String?): QuotaResponse {
        val response = client.get("$baseHost/api/quota") {
            header("X-Install-Id", installId)
            accessToken?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        }
        if (response.status.value !in 200..299) {
            throw ApiException(response.status.value, response.bodyAsText())
        }
        return response.body<QuotaResponse>()
    }

    /**
     * 即时激活/对账：后端权威核对赞助并 upsert，返回最新 Pro 态。用户从 Sponsors 返回时调用。
     * 返回整个响应而非裸 Boolean——调用方要靠 `reason` 区分「没赞助」与「赞助了但没关联 GitHub」。
     */
    open suspend fun refreshPro(accessToken: String): ProRefreshResponse {
        val response = client.post("$baseHost/api/pro/refresh") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        if (response.status.value !in 200..299) {
            throw ApiException(response.status.value, response.bodyAsText())
        }
        return response.body<ProRefreshResponse>()
    }

    /** 应用级配置（min_version 强更开关等），冷启动拉取，失败由调用方静默处理。 */
    open suspend fun fetchAppConfig(): AppConfigResponse {
        val response = client.get("$baseHost/api/app-config")
        if (response.status.value !in 200..299) {
            throw ApiException(response.status.value, response.bodyAsText())
        }
        return response.body<AppConfigResponse>()
    }

    /** 聊天可选模型目录 + 服务端默认（公开只读；后端从 OpenAI 动态取 + 缓存）。 */
    open suspend fun fetchChatModels(): ChatModelsResponse {
        val response = client.get("$baseHost/api/chat/models")
        if (response.status.value !in 200..299) {
            throw ApiException(response.status.value, response.bodyAsText())
        }
        return response.body<ChatModelsResponse>()
    }

    // ---- 收藏云同步（设计稿 2026-07-24-favorites-cloud-sync，B 档）----
    // 全部经 Bearer 鉴权；调用方（FavoriteRepository）负责传入 externalId 已回填的条目。

    /** 拉取该用户全量收藏。非 2xx 抛 [ApiException]，供调用方在失败时保留本地、不覆盖。 */
    open suspend fun fetchFavorites(accessToken: String): List<FavoriteItem> {
        val response = client.get("$baseHost/api/favorites") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        if (response.status.value !in 200..299) {
            throw ApiException(response.status.value, response.bodyAsText())
        }
        return response.body<FavoritesResponse>().favorites
    }

    /** upsert 单条收藏（幂等）。返回是否成功，失败由调用方入队重试。 */
    open suspend fun putFavorite(accessToken: String, item: FavoriteItem): Boolean {
        val response = client.put("$baseHost/api/favorites") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody(item)
        }
        return response.status.value in 200..299
    }

    /** 删一条收藏（幂等，服务端不存在也 200）。 */
    open suspend fun deleteFavorite(accessToken: String, source: String, externalId: String): Boolean {
        val response = client.delete("$baseHost/api/favorites") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            parameter("source", source)
            parameter("external_id", externalId)
        }
        return response.status.value in 200..299
    }

    /** 批量 upsert（登录首次合并本地收藏）。 */
    open suspend fun batchPutFavorites(accessToken: String, items: List<FavoriteItem>): Boolean {
        val response = client.post("$baseHost/api/favorites/batch") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody(FavoritesResponse(items))
        }
        return response.status.value in 200..299
    }

    open suspend fun cancelSubscribe(email: String): Result<SubscribeResponse> {
        return try {
            val response = client.post("$baseHost/api/subscribe") {
                parameter("action", "cancel")
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("email", email)
                })
            }
            if (response.status.value in 200..299) {
                Result.success(response.body<SubscribeResponse>())
            } else {
                Result.failure(ApiException(response.status.value, response.bodyAsText()))
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }
}
