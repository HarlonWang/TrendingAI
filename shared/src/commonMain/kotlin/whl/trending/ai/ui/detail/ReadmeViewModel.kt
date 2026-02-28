package whl.trending.ai.ui.detail

import whl.trending.ai.data.repository.TrendingRepository

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReadmeUiState(
    val content: String = "",
    val branch: String = "",
    val isLoading: Boolean = true,
    val error: String? = null
)

class ReadmeViewModel(
    private val owner: String,
    private val repo: String,
    private val repository: TrendingRepository = TrendingRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReadmeUiState())
    val uiState: StateFlow<ReadmeUiState> = _uiState.asStateFlow()

    init {
        fetchReadme()
    }

    fun fetchReadme() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val response = repository.getReadme(owner, repo)
                if (response.success) {
                    _uiState.update { it.copy(content = response.content, branch = response.branch, isLoading = false) }
                } else {
                    _uiState.update { it.copy(error = response.error, isLoading = false) }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.update { it.copy(error = e.message ?: "Unknown Error", isLoading = false) }
            }
        }
    }
}
