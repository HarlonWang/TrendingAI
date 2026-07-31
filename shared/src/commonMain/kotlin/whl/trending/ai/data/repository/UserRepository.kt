package whl.trending.ai.data.repository

import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.data.model.MeResponse
import whl.trending.ai.data.model.MeUser
import whl.trending.ai.core.platform.trackEvent
import whl.trending.ai.data.model.ProRefreshResponse
import whl.trending.ai.data.model.QuotaResponse
import whl.trending.ai.data.remote.ApiException
import whl.trending.ai.data.remote.TrendingApi

// open：便于测试以子类替身注入（保持手动 DI，不引入 mock 框架）
open class UserRepository(private val api: TrendingApi = TrendingApi()) {

    // 真正的测试注入点：覆写此方法即可同时影响 fetchMe 与 syncMe（二者都经它取数）。
    // 注意：只覆写下面的 fetchMe 不影响 syncMe——syncMe 直接调 fetchMeResponse，会打真网络。
    open suspend fun fetchMeResponse(accessToken: String, fresh: Boolean = false): MeResponse =
        api.fetchMe(accessToken, fresh)

    open suspend fun fetchMe(accessToken: String): MeUser = fetchMeResponse(accessToken).user

    /**
     * credits 余额（账户页配额卡）。X-Install-Id 恒传（匿名记账主体）；token 可空——
     * 服务端据此定档。不做缓存：余额是每次请求都在变的个人数据，进页即拉实时值。
     */
    open suspend fun fetchQuota(accessToken: String?): QuotaResponse =
        api.fetchQuota(globalSettingsManager.getOrCreateInstallId(), accessToken)

    /**
     * 登录成功/应用启动（已登录）时调用：服务端建档 + 刷新 last_login_at，并缓存头像与 Pro 权益态。
     * 失败静默——下次打开 Profile 仍会重试，不阻塞登录主流程。
     */
    suspend fun syncMe(accessToken: String?, fresh: Boolean = false): MeUser? {
        if (accessToken == null) return null
        return try {
            val me = fetchMeResponse(accessToken, fresh)
            globalSettingsManager.setUserAvatarUrl(me.user.avatarUrl)
            globalSettingsManager.setGithubIdentity(me.user.githubLogin, me.user.githubUserId)
            globalSettingsManager.setUserEmail(me.user.email)
            globalSettingsManager.setIsPro(me.pro)
            me.user
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    /**
     * 即时对账 Pro 权益：调 /api/pro/refresh（后端 PAT 权威核对赞助）并刷新本地 isPro 缓存。
     * 用户从 Sponsors 页返回（ON_RESUME）时调用，实现「赞助完回来即生效」。失败静默返回 null。
     *
     * 返回整个响应而非裸 Boolean：调用方需要 `reason` 才能区分「查证没赞助」与
     * 「赞助了但账户没关联 GitHub」——后者裸 false 会让用户对着无声的失败自己摸索。
     */
    suspend fun refreshPro(accessToken: String?): ProRefreshResponse? {
        if (accessToken == null) return null
        return try {
            val result = api.refreshPro(accessToken)
            globalSettingsManager.setIsPro(result.pro)
            result
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            // 这条路径只在赞助对账窗口内跑（用户刚从 Sponsors 页回来），量极小而诊断价值极高：
            // 失败意味着「钱付了，app 却说不清状态」——正是 P0-4 那类问题复发的信号，
            // 静默吞掉就等于把它藏起来。只报状态码：token 与响应体都可能含敏感信息。
            trackEvent("pro_refresh_failed", mapOf("status" to ((e as? ApiException)?.statusCode ?: -1)))
            null
        }
    }
}
