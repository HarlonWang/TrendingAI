package whl.trending.ai.ui.feed

import whl.trending.ai.data.model.FeedItem
import whl.trending.ai.data.repository.TrendingRepository

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FeedUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val items: List<FeedItem> = emptyList()
)

class FeedViewModel(
    private val source: String,
    private val repository: TrendingRepository = TrendingRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    private var fetchJob: Job? = null

    init {
        fetchFeed()
    }

    private fun fetchFeed(isRefresh: Boolean = false) {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            if (isRefresh) {
                _uiState.update { it.copy(isRefreshing = true, error = null) }
                delay(500)
            } else {
                _uiState.update { it.copy(isLoading = true, error = null) }
            }
            try {
                val response = repository.getFeed(source)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        items = response.data,
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
        fetchFeed(isRefresh = true)
    }

    fun retry() {
        fetchFeed()
    }
}
