package whl.trending.ai.auth

import whl.trending.ai.data.remote.GithubApi
import whl.trending.ai.data.remote.GithubFollowing

data class FollowingInfo(
    val users: Set<String>,   // 全小写 login
    val orgs: Set<String>,    // 全小写 login
)

/**
 * 关注列表会话级缓存：按 type 分桶（User / Organization），all lowercase。
 * 任何异常返回 null（CancellationException 透传），null 不缓存。
 * 登出时由 LogtoAuthManager 调用 clear() 清缓存。
 */
open class FollowingProvider(
    private val githubApi: GithubApi = GithubApi(),
    private val tokenProvider: GithubTokenProvider = GithubTokenProvider.shared,
) {
    private var cached: FollowingInfo? = null

    open suspend fun get(): FollowingInfo? {
        cached?.let { return it }
        val token = tokenProvider.get() ?: return null
        return try {
            val users = mutableSetOf<String>()
            val orgs = mutableSetOf<String>()
            var page = 1
            val maxPages = 10
            while (page <= maxPages) {
                val batch: List<GithubFollowing> = githubApi.fetchFollowing(token, page)
                if (batch.isEmpty()) break
                for (item in batch) {
                    val loginLower = item.login.lowercase()
                    if (item.type == "Organization") orgs.add(loginLower)
                    else users.add(loginLower)
                }
                if (batch.size < 100) break
                page++
            }
            FollowingInfo(users = users, orgs = orgs).also { cached = it }
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
        val shared = FollowingProvider()
    }
}
