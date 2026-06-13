package whl.trending.ai.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import whl.trending.ai.auth.GithubTokenProvider
import whl.trending.ai.data.remote.GithubApi
import whl.trending.ai.data.remote.GithubRepoSummary

private const val PAGE_SIZE = 30

data class RepoListUiState(
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val items: List<GithubRepoSummary> = emptyList(),
    val isError: Boolean = false,
    val endReached: Boolean = false,
)

/** 自有仓库下钻列表：分页拉取（按最近 push 倒序，触底加载下一页）。 */
class RepoListViewModel(
    private val githubApi: GithubApi = GithubApi(),
    private val tokenProvider: GithubTokenProvider = GithubTokenProvider.shared,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RepoListUiState())
    val uiState: StateFlow<RepoListUiState> = _uiState.asStateFlow()

    private var nextPage = 1
    private var loadJob: Job? = null

    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = RepoListUiState(isLoading = true)
            nextPage = 1
            val token = tokenProvider.get()
            if (token == null) {
                _uiState.value = RepoListUiState(isLoading = false, isError = true)
                return@launch
            }
            try {
                val page = githubApi.fetchReposPage(token, nextPage, PAGE_SIZE)
                nextPage++
                _uiState.value = RepoListUiState(
                    isLoading = false,
                    items = page,
                    endReached = page.size < PAGE_SIZE,
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.value = RepoListUiState(isLoading = false, isError = true)
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || state.endReached) return
        loadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)
            val token = tokenProvider.get()
            if (token == null) {
                _uiState.value = _uiState.value.copy(isLoadingMore = false, endReached = true)
                return@launch
            }
            try {
                val page = githubApi.fetchReposPage(token, nextPage, PAGE_SIZE)
                nextPage++
                val merged = (_uiState.value.items + page).distinctBy { it.fullName }
                _uiState.value = _uiState.value.copy(
                    isLoadingMore = false,
                    items = merged,
                    endReached = page.size < PAGE_SIZE,
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.value = _uiState.value.copy(isLoadingMore = false, endReached = true)
            }
        }
    }

    /** 离开页面：取消在途加载并重置为初始 loading 态（VM 为 Activity 级缓存）。 */
    fun onLeave() {
        loadJob?.cancel()
        _uiState.value = RepoListUiState()
    }
}
