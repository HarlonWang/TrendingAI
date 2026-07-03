package whl.trending.ai.ui.picks

import whl.trending.ai.data.model.PicksResponse
import whl.trending.ai.data.repository.TrendingRepository
import whl.trending.ai.data.local.LastDataCache
import whl.trending.ai.data.local.SettingsManager
import whl.trending.ai.data.local.globalLastDataCache
import whl.trending.ai.data.local.globalSettingsManager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PicksUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val picks: PicksResponse? = null
)

class PicksViewModel(
    private val repository: TrendingRepository = TrendingRepository(),
    private val settingsManager: SettingsManager = globalSettingsManager,
    private val cache: LastDataCache = globalLastDataCache
) : ViewModel() {
    private val _uiState = MutableStateFlow(PicksUiState())
    val uiState: StateFlow<PicksUiState> = _uiState.asStateFlow()

    private var fetchJob: Job? = null

    init {
        loadWithCache()

        viewModelScope.launch {
            // drop(1) 丢弃首次初始化的当前值，只监听真正发生的设置修改，避免初始化时重复调用 fetchPicks
            settingsManager.appLanguage.drop(1).collect {
                fetchPicks(isRefresh = true)
            }
        }
    }

    private fun cacheKey(lang: String) = "picks_$lang"

    /** SWR：有缓存先展示 + 顶部指示器自动刷新（不加防闪烁延迟）；无缓存走骨架屏 */
    private fun loadWithCache() {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            val cached = cache.get<PicksResponse>(cacheKey(settingsManager.currentContentLang()))
            if (cached != null) {
                _uiState.update { it.copy(isLoading = false, isRefreshing = true, picks = cached) }
            }
            fetch()
        }
    }

    private fun fetchPicks(isRefresh: Boolean = false) {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            if (isRefresh) {
                _uiState.update { it.copy(isRefreshing = true, error = null) }
                // 防指示器闪烁延迟，仅手动下拉/设置变更触发的刷新需要
                delay(500)
            } else {
                _uiState.update { it.copy(isLoading = true, error = null) }
            }
            fetch()
        }
    }

    private suspend fun fetch() {
        try {
            val summaryLang = settingsManager.currentContentLang()
            val response = repository.getPicks(summaryLang)
            cache.put(cacheKey(summaryLang), response)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    picks = response,
                    error = null
                )
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    // 有内容（缓存或旧数据）时刷新失败静默保留，避免整页错误盖掉可用内容
                    error = if (it.picks == null) e.message ?: "Unknown Error" else null
                )
            }
        }
    }

    fun refresh() {
        fetchPicks(isRefresh = true)
    }

    fun retry() {
        fetchPicks()
    }
}
