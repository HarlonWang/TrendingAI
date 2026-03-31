package whl.trending.ai.ui.picks

import whl.trending.ai.data.model.PicksResponse
import whl.trending.ai.data.repository.TrendingRepository

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PicksUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val picks: PicksResponse? = null
)

class PicksViewModel(
    private val repository: TrendingRepository = TrendingRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(PicksUiState())
    val uiState: StateFlow<PicksUiState> = _uiState.asStateFlow()

    private var fetchJob: Job? = null

    init {
        fetchPicks()
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
                val response = repository.getPicks()
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
