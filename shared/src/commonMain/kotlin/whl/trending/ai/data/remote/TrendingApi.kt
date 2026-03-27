package whl.trending.ai.data.remote

import whl.trending.ai.data.model.ReadmeResponse
import whl.trending.ai.data.model.TrendingResponse

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

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

    open suspend fun fetchReadme(owner: String, repo: String): ReadmeResponse {
        val response = client.get("$baseHost/api/readme") {
            parameter("owner", owner)
            parameter("repo", repo)
        }
        return response.body<ReadmeResponse>()
    }
}
