package whl.trending.ai.data.repository

import whl.trending.ai.data.model.PicksResponse
import whl.trending.ai.data.model.ReadmeResponse
import whl.trending.ai.data.model.TrendingResponse
import whl.trending.ai.data.remote.TrendingApi

class TrendingRepository(private val api: TrendingApi = TrendingApi()) {
    suspend fun getPicks(): PicksResponse {
        return api.fetchPicks()
    }

    suspend fun getReadme(owner: String, repo: String): ReadmeResponse {
        return api.fetchReadme(owner, repo)
    }

    suspend fun getTrending(
        period: String,
        language: String,
        summaryLang: String,
        date: String? = null,
        batch: String? = null
    ): TrendingResponse {
        return api.fetchTrending(period, language, summaryLang, date, batch)
    }

    suspend fun submitFeedback(content: String, email: String?): Result<Unit> {
        return api.submitFeedback(content, email)
    }
}
