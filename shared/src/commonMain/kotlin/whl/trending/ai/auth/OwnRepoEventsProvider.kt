package whl.trending.ai.auth

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import whl.trending.ai.data.remote.GithubApi
import whl.trending.ai.data.remote.GithubEventDto

/**
 * 自有仓库事件会话级缓存（精选档规则3 数据源）：仓库按 pushed 排序取前 N 个，
 * 并发拉取各仓库 events 后合并。任何异常返回 null（CancellationException 透传），null 不缓存。
 * 登出时由 LogtoAuthManager 调用 clear() 清缓存。
 */
open class OwnRepoEventsProvider(
    private val githubApi: GithubApi = GithubApi(),
    private val tokenProvider: GithubTokenProvider = GithubTokenProvider.shared,
) {
    private var cached: List<GithubEventDto>? = null

    open suspend fun get(): List<GithubEventDto>? {
        cached?.let { return it }
        val token = tokenProvider.get() ?: return null
        return try {
            val repoNames = githubApi.fetchOwnRepos(token, perPage = MAX_REPOS)
            val events = coroutineScope {
                repoNames.map { fullName ->
                    async {
                        runCatching { githubApi.fetchRepoEvents(token, fullName) }
                            .getOrElse { e ->
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                emptyList()
                            }
                    }
                }.map { it.await() }.flatten()
            }
            events.also { cached = it }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    fun clear() {
        cached = null
    }

    companion object {
        /** 仓库列表已按 pushed 排序，最近活跃的前 20 个足以覆盖规则3 的 star/fork 来源 */
        private const val MAX_REPOS = 20

        /** 全局共享实例：登出时由 LogtoAuthManager 清空 */
        val shared = OwnRepoEventsProvider()
    }
}
