package whl.trending.ai.data.repository

import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.data.model.MeResponse
import whl.trending.ai.data.model.MeUser
import whl.trending.ai.data.remote.TrendingApi

// open：便于测试以子类替身注入（保持手动 DI，不引入 mock 框架）
open class UserRepository(private val api: TrendingApi = TrendingApi()) {

    // 真正的测试注入点：覆写此方法即可同时影响 fetchMe 与 syncMe（二者都经它取数）。
    // 注意：只覆写下面的 fetchMe 不影响 syncMe——syncMe 直接调 fetchMeResponse，会打真网络。
    open suspend fun fetchMeResponse(accessToken: String): MeResponse = api.fetchMe(accessToken)

    open suspend fun fetchMe(accessToken: String): MeUser = fetchMeResponse(accessToken).user

    /**
     * 登录成功/应用启动（已登录）时调用：服务端建档 + 刷新 last_login_at，并缓存头像与 Pro 权益态。
     * 失败静默——下次打开 Profile 仍会重试，不阻塞登录主流程。
     */
    suspend fun syncMe(accessToken: String?): MeUser? {
        if (accessToken == null) return null
        return try {
            val me = fetchMeResponse(accessToken)
            globalSettingsManager.setUserAvatarUrl(me.user.avatarUrl)
            globalSettingsManager.setGithubIdentity(me.user.githubLogin, me.user.githubUserId)
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
     */
    suspend fun refreshPro(accessToken: String?): Boolean? {
        if (accessToken == null) return null
        return try {
            val pro = api.refreshPro(accessToken)
            globalSettingsManager.setIsPro(pro)
            pro
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }
}
