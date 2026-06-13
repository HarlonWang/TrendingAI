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
import whl.trending.ai.data.remote.GithubUserSummary

enum class GithubUserListMode { FOLLOWERS, FOLLOWING }

private const val PAGE_SIZE = 30

data class GithubUserListUiState(
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val items: List<GithubUserSummary> = emptyList(),
    val isError: Boolean = false,
    val endReached: Boolean = false,
)

/** Followers / Following 下钻列表：分页拉取（触底加载下一页）。 */
class GithubUserListViewModel(
    private val mode: GithubUserListMode,
    private val githubApi: GithubApi = GithubApi(),
    private val tokenProvider: GithubTokenProvider = GithubTokenProvider.shared,
) : ViewModel() {
    private val _uiState = MutableStateFlow(GithubUserListUiState())
    val uiState: StateFlow<GithubUserListUiState> = _uiState.asStateFlow()

    private var nextPage = 1
    private var loadJob: Job? = null

    private suspend fun fetchPage(token: String, page: Int): List<GithubUserSummary> =
        when (mode) {
            GithubUserListMode.FOLLOWERS -> githubApi.fetchFollowers(token, page, PAGE_SIZE)
            GithubUserListMode.FOLLOWING -> githubApi.fetchFollowingUsers(token, page, PAGE_SIZE)
        }

    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = GithubUserListUiState(isLoading = true)
            nextPage = 1
            val token = tokenProvider.get()
            if (token == null) {
                _uiState.value = GithubUserListUiState(isLoading = false, isError = true)
                return@launch
            }
            try {
                val page = fetchPage(token, nextPage)
                nextPage++
                _uiState.value = GithubUserListUiState(
                    isLoading = false,
                    items = page,
                    endReached = page.size < PAGE_SIZE,
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.value = GithubUserListUiState(isLoading = false, isError = true)
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
                val page = fetchPage(token, nextPage)
                nextPage++
                val merged = (_uiState.value.items + page).distinctBy { it.login }
                _uiState.value = _uiState.value.copy(
                    isLoadingMore = false,
                    items = merged,
                    endReached = page.size < PAGE_SIZE,
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                // 翻页失败不致命：保留已加载内容，停止继续拉取
                _uiState.value = _uiState.value.copy(isLoadingMore = false, endReached = true)
            }
        }
    }

    /** 离开页面：取消在途加载并重置为初始 loading 态（VM 为 Activity 级缓存）。 */
    fun onLeave() {
        loadJob?.cancel()
        _uiState.value = GithubUserListUiState()
    }
}
