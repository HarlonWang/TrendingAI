package whl.trending.ai.ui.picks

import whl.trending.ai.data.model.PicksResponse
import whl.trending.ai.data.repository.TrendingRepository
import whl.trending.ai.data.local.SettingsManager
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
    private val settingsManager: SettingsManager = globalSettingsManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(PicksUiState())
    val uiState: StateFlow<PicksUiState> = _uiState.asStateFlow()

    private var fetchJob: Job? = null

    init {
        fetchPicks()

        viewModelScope.launch {
            // drop(1) 丢弃首次初始化的当前值，只监听真正发生的设置修改，避免初始化时重复调用 fetchPicks
            settingsManager.appLanguage.drop(1).collect {
                fetchPicks(isRefresh = true)
            }
        }
    }

    private fun fetchPicks(isRefresh: Boolean = false) {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            if (isRefresh) {
                _uiState.update { it.copy(isRefreshing = true, error = null) }
                delay(500)
            } else {
                _uiState.update { it.copy(isLoading = true, error = null) }
            }
            try {
                val summaryLang = settingsManager.currentContentLang()
                val response = repository.getPicks(summaryLang)
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
                        error = e.message ?: "Unknown Error"
                    )
                }
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
