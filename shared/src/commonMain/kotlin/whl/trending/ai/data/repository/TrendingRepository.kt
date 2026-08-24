package whl.trending.ai.data.repository

import whl.trending.ai.data.model.FeedResponse
import whl.trending.ai.data.model.PicksResponse
import whl.trending.ai.data.model.ReadmeResponse
import whl.trending.ai.data.model.SubscribeResponse
import whl.trending.ai.data.model.TrendingResponse
import whl.trending.ai.data.remote.TrendingApi

// open：便于测试以子类替身注入（保持手动 DI，不引入 mock 框架）
open class TrendingRepository(private val api: TrendingApi = TrendingApi()) {

    companion object {
        /** 无状态、可安全共享的便捷单例；测试注入仍走构造参数。 */
        val shared: TrendingRepository by lazy { TrendingRepository() }
    }

    open suspend fun getFeed(source: String, summaryLang: String = "zh"): FeedResponse {
        return api.fetchFeed(source, summaryLang)
    }

    open suspend fun getPicks(summaryLang: String = "zh"): PicksResponse {
        return api.fetchPicks(summaryLang)
    }

    suspend fun getReadme(owner: String, repo: String): ReadmeResponse {
        return api.fetchReadme(owner, repo)
    }

    open suspend fun getTrending(
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

    suspend fun subscribe(
        email: String,
        source: String,
        lang: String,
    ): Result<SubscribeResponse> {
        return api.submitSubscribe(email, source, lang)
    }

    suspend fun cancelSubscribe(email: String): Result<SubscribeResponse> {
        return api.cancelSubscribe(email)
    }
}
