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
        /**
         * 进程级共享实例：TrendingApi 每个实例各建一个从不关闭的 HttpClient，组合树里随手 new
         * 会积累引擎/连接池（仿 ChatApi.shared 的收敛方式）。无状态可安全共享；测试注入仍走构造参数。
         */
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
        newsletter: Boolean? = null,
    ): Result<SubscribeResponse> {
        return api.submitSubscribe(email, source, lang, newsletter)
    }

    suspend fun cancelSubscribe(email: String): Result<SubscribeResponse> {
        return api.cancelSubscribe(email)
    }
}
