package whl.trending.ai.ui.feedback

import whl.trending.ai.data.repository.TrendingRepository

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface FeedbackEvent {
    data object Success : FeedbackEvent
    data class Error(val isRateLimit: Boolean) : FeedbackEvent
}

data class FeedbackUiState(
    val content: String = "",
    val email: String = "",
    val isEmailValid: Boolean = true,
    val isSubmitting: Boolean = false
)

class FeedbackViewModel(
    private val repository: TrendingRepository = TrendingRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(FeedbackUiState())
    val uiState: StateFlow<FeedbackUiState> = _uiState.asStateFlow()

    private val _events = Channel<FeedbackEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun updateContent(value: String) {
        _uiState.update { it.copy(content = value) }
    }

    fun updateEmail(value: String) {
        val valid = value.isBlank() || EMAIL_REGEX.matches(value.trim())
        _uiState.update { it.copy(email = value, isEmailValid = valid) }
    }

    fun submit() {
        val state = _uiState.value
        val content = state.content.trim()
        if (content.isEmpty() || content.length > 2000) return
        if (!state.isEmailValid) return
        if (state.isSubmitting) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            val email = state.email.trim().ifEmpty { null }
            val result = repository.submitFeedback(content, email)
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(content = "", email = "", isEmailValid = true, isSubmitting = false) }
                    _events.send(FeedbackEvent.Success)
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isSubmitting = false) }
                    _events.send(FeedbackEvent.Error(isRateLimit = e.message?.contains("429") == true))
                }
            )
        }
    }

    companion object {
        private val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    }
}
