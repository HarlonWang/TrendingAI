package whl.trending.ai.data.repository

import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.data.model.MeUser
import whl.trending.ai.data.remote.TrendingApi

// open：便于测试以子类替身注入（保持手动 DI，不引入 mock 框架）
open class UserRepository(private val api: TrendingApi = TrendingApi()) {

    open suspend fun fetchMe(accessToken: String): MeUser = api.fetchMe(accessToken).user

    /**
     * 登录成功/应用启动（已登录）时调用：服务端建档 + 刷新 last_login_at，并缓存头像。
     * 失败静默——下次打开 Profile 仍会重试，不阻塞登录主流程。
     */
    suspend fun syncMe(accessToken: String?): MeUser? {
        if (accessToken == null) return null
        return try {
            val user = fetchMe(accessToken)
            globalSettingsManager.setUserAvatarUrl(user.avatarUrl)
            user
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }
}
