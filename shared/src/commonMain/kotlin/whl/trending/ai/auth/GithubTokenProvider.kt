package whl.trending.ai.auth

import whl.trending.ai.data.remote.GithubTokenApi

/** 一次 GitHub token 取回的结果。区分「服务端明确没存」与「没问到」，两者的 UI 归宿不同。 */
sealed interface GithubTokenLookup {
    data class Available(val token: String) : GithubTokenLookup

    /** 服务端 404：纯邮箱账号，或已关联但 vault 为空（被清空过）。恢复手段只有再走一次 GitHub OAuth。 */
    data object Missing : GithubTokenLookup

    /** 网络或其他错误，有没有 token 未知，不应据此引导用户做任何事。 */
    data object Failed : GithubTokenLookup
}

/**
 * GitHub token 会话级缓存。GitHub OAuth App 的 access token 永不过期
 * （用户主动撤销授权才失效），进程内取一次即可；不落盘，避免明文持久化第三方凭据。
 */
open class GithubTokenProvider(
    private val accountApi: GithubTokenApi = GithubTokenApi(),
) {
    private var cached: String? = null

    open suspend fun lookup(): GithubTokenLookup {
        cached?.let { return GithubTokenLookup.Available(it) }
        return try {
            val token = accountApi.fetchGithubToken() ?: return GithubTokenLookup.Missing
            cached = token
            GithubTokenLookup.Available(token)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            GithubTokenLookup.Failed
        }
    }

    /** 只关心「能不能拿到」的调用方用这个；要区分没存与没问到的走 [lookup]。 */
    open suspend fun get(): String? = (lookup() as? GithubTokenLookup.Available)?.token

    fun clear() {
        cached = null
    }

    companion object {
        /** 全局共享实例：登出时由 LoginbaseAuthManager 清空 */
        val shared = GithubTokenProvider()
    }
}
