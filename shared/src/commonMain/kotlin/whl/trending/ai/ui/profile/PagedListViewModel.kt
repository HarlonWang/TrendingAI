package whl.trending.ai.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import whl.trending.ai.auth.GithubTokenProvider

data class PagedListUiState<T>(
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val items: List<T> = emptyList(),
    val isError: Boolean = false,
    val endReached: Boolean = false,
)

/**
 * GitHub 下钻列表的通用分页 VM：首屏 load + 触底 loadMore + 离开重置。
 * 子类只需提供 [pageSize]、[fetchPage]（取第 N 页）与 [keyOf]（去重键）。
 * 错误处理：首屏失败置 isError；翻页失败保留已加载内容并置 endReached（不致命）。
 */
abstract class PagedListViewModel<T>(
    protected val tokenProvider: GithubTokenProvider = GithubTokenProvider.shared,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PagedListUiState<T>())
    val uiState: StateFlow<PagedListUiState<T>> = _uiState.asStateFlow()

    private var nextPage = 1
    private var loadJob: Job? = null

    protected abstract val pageSize: Int
    protected abstract suspend fun fetchPage(token: String, page: Int): List<T>
    protected abstract fun keyOf(item: T): Any

    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = PagedListUiState(isLoading = true)
            nextPage = 1
            val token = tokenProvider.get()
            if (token == null) {
                _uiState.value = PagedListUiState(isLoading = false, isError = true)
                return@launch
            }
            try {
                val page = fetchPage(token, nextPage)
                nextPage++
                _uiState.value = PagedListUiState(
                    isLoading = false,
                    items = page,
                    endReached = page.size < pageSize,
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.value = PagedListUiState(isLoading = false, isError = true)
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
                val merged = (_uiState.value.items + page).distinctBy { keyOf(it) }
                _uiState.value = _uiState.value.copy(
                    isLoadingMore = false,
                    items = merged,
                    endReached = page.size < pageSize,
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
        _uiState.value = PagedListUiState()
    }
}
