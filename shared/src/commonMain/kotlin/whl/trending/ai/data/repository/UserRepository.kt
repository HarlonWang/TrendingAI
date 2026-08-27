package whl.trending.ai.data.repository

import whl.trending.ai.core.analytics.AppEvent
import whl.trending.ai.core.analytics.setAnalyticsUser
import whl.trending.ai.core.analytics.track
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.data.model.MeResponse
import whl.trending.ai.data.model.MeUser
import whl.trending.ai.data.model.ProRefreshResponse
import whl.trending.ai.data.model.QuotaResponse
import whl.trending.ai.data.remote.ApiException
import whl.trending.ai.data.remote.TrendingApi

// open：便于测试以子类替身注入（保持手动 DI，不引入 mock 框架）
open class UserRepository(private val api: TrendingApi = TrendingApi()) {

    // 真正的测试注入点：只覆写下面的 fetchMe 不影响 syncMe——syncMe 直接调这里，会打真网络
    open suspend fun fetchMeResponse(fresh: Boolean = false): MeResponse = api.fetchMe(fresh)

    open suspend fun fetchMe(): MeUser = fetchMeResponse().user

    /** credits 余额。X-Install-Id 恒传，token 有无由鉴权插件决定（服务端据此定档）；不做缓存。 */
    open suspend fun fetchQuota(): QuotaResponse =
        api.fetchQuota(globalSettingsManager.getOrCreateInstallId())

    /**
     * 登录成功/应用启动（已登录）时调用：服务端建档 + 刷新 last_login_at，并缓存头像与 Pro 权益态。
     * 失败静默——下次打开 Profile 仍会重试，不阻塞登录主流程。**调用方负责只在登录态调**。
     */
    suspend fun syncMe(fresh: Boolean = false): MeUser? =
        try {
            val me = fetchMeResponse(fresh)
            globalSettingsManager.setUserAvatarUrl(me.user.avatarUrl)
            globalSettingsManager.setGithubIdentity(me.user.githubLogin, me.user.githubUserId)
            globalSettingsManager.setUserEmail(me.user.email)
            globalSettingsManager.setIsPro(me.pro)
            // 埋点关联账号：此后事件带 user_id，服务端据此建 install↔identity 映射
            setAnalyticsUser(me.user.userId)
            me.user
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }

    /**
     * 即时对账 Pro 权益：调 /api/pro/refresh 并刷新本地 isPro 缓存。失败静默返回 null。
     * 返回整个响应而非裸 Boolean：调用方需要 `reason` 区分「查证没赞助」与「赞助了但没关联 GitHub」。
     */
    suspend fun refreshPro(): ProRefreshResponse? {
        return try {
            val result = api.refreshPro()
            globalSettingsManager.setIsPro(result.pro)
            result
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            // 失败意味着「钱付了，app 说不清状态」，必须留痕；只报状态码——token 与响应体可能含敏感信息
            track(AppEvent.ApiFailed("pro/refresh", (e as? ApiException)?.statusCode ?: -1))
            null
        }
    }
}
