package whl.trending.ai.auth

import whl.trending.ai.data.remote.GithubTokenApi

/**
 * GitHub token 会话级缓存。GitHub OAuth App 的 access token 永不过期
 * （用户主动撤销授权才失效），进程内取一次即可；不落盘，避免明文持久化第三方凭据。
 *
 * token 来源自 2026-08-13 起由 Logto Secret Vault 换成自家后端的加密保管
 * （见 [GithubTokenApi]）；本类逻辑不变。
 */
open class GithubTokenProvider(
    private val accountApi: GithubTokenApi = GithubTokenApi(),
    private val authManager: () -> AuthManager = { globalAuthManager },
) {
    private var cached: String? = null

    open suspend fun get(): String? {
        cached?.let { return it }
        return try {
            // authorized：自家 token 过期时刷新重试，否则 GitHub 数据面会在
            // access token 过期后整片失效（表现为「未关联 GitHub」的假象）
            authManager().authorized { accountApi.fetchGithubToken(it) }?.also { cached = it }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    fun clear() {
        cached = null
    }

    companion object {
        /** 全局共享实例：登出时由 LoginbaseAuthManager 清空 */
        val shared = GithubTokenProvider()
    }
}
