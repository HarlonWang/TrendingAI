package whl.trending.ai.ui.profile

import whl.trending.ai.auth.GithubTokenProvider
import whl.trending.ai.data.remote.GithubApi
import whl.trending.ai.data.remote.GithubRepoSummary

/** 自有公开仓库下钻列表：按最近 push 倒序，分页逻辑见 [PagedListViewModel]。 */
class RepoListViewModel(
    private val githubApi: GithubApi = GithubApi(),
    tokenProvider: GithubTokenProvider = GithubTokenProvider.shared,
) : PagedListViewModel<GithubRepoSummary>(tokenProvider) {
    override val pageSize = 30

    override suspend fun fetchPage(token: String, page: Int): List<GithubRepoSummary> =
        githubApi.fetchReposPage(token, page, pageSize)

    override fun keyOf(item: GithubRepoSummary): Any = item.fullName
}
