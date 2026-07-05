package whl.trending.ai.data.remote

import whl.trending.ai.core.platform.getUserAgent
import whl.trending.ai.data.model.FeedResponse
import whl.trending.ai.data.model.MeResponse
import whl.trending.ai.data.model.PicksResponse
import whl.trending.ai.data.model.ReadmeResponse
import whl.trending.ai.data.model.SubscribeResponse
import whl.trending.ai.data.model.TrendingResponse

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
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

    /** [newsletter] 仅 waitlist 场景使用：显式 true 才开通每日邮件（服务端严格 opt-in），null 不传字段 */
    open suspend fun submitSubscribe(
        email: String,
        source: String,
        lang: String,
        newsletter: Boolean? = null,
    ): Result<SubscribeResponse> {
        return try {
            val response = client.post("$baseHost/api/subscribe") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("email", email)
                    put("source", source)
                    put("lang", lang)
                    newsletter?.let { put("newsletter", it) }
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

    open suspend fun fetchMe(accessToken: String): MeResponse {
        val response = client.get("$baseHost/api/me") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        if (response.status.value !in 200..299) {
            throw ApiException(response.status.value, response.bodyAsText())
        }
        return response.body<MeResponse>()
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
