package whl.trending.ai.ui.profile

import whl.trending.ai.auth.GithubTokenProvider
import whl.trending.ai.data.remote.GithubApi
import whl.trending.ai.data.remote.GithubUserSummary

enum class GithubUserListMode { FOLLOWERS, FOLLOWING }

/** Followers / Following 下钻列表：靠 [mode] 切换数据源，分页逻辑见 [PagedListViewModel]。 */
class GithubUserListViewModel(
    private val mode: GithubUserListMode,
    private val githubApi: GithubApi = GithubApi(),
    tokenProvider: GithubTokenProvider = GithubTokenProvider.shared,
) : PagedListViewModel<GithubUserSummary>(tokenProvider) {
    override val pageSize = 30

    override suspend fun fetchPage(token: String, page: Int): List<GithubUserSummary> =
        when (mode) {
            GithubUserListMode.FOLLOWERS -> githubApi.fetchFollowers(token, page, pageSize)
            GithubUserListMode.FOLLOWING -> githubApi.fetchFollowingUsers(token, page, pageSize)
        }

    override fun keyOf(item: GithubUserSummary): Any = item.login
}
