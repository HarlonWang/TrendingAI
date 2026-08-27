package whl.trending.ai.auth

import whl.trending.ai.data.remote.GithubTokenApi

/**
 * GitHub token 会话级缓存。GitHub OAuth App 的 access token 永不过期
 * （用户主动撤销授权才失效），进程内取一次即可；不落盘，避免明文持久化第三方凭据。
 */
open class GithubTokenProvider(
    private val accountApi: GithubTokenApi = GithubTokenApi(),
) {
    private var cached: String? = null

    open suspend fun get(): String? {
        cached?.let { return it }
        return try {
            accountApi.fetchGithubToken()?.also { cached = it }
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
