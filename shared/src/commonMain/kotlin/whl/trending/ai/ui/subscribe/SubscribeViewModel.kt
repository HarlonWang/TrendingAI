package whl.trending.ai.ui.subscribe

import whl.trending.ai.core.platform.isIosPlatform
import whl.trending.ai.data.local.SettingsManager
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.data.model.SubscribeStatus
import whl.trending.ai.data.remote.ApiException
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

sealed interface SubscribeEvent {
    data class Success(val status: SubscribeStatus) : SubscribeEvent
    data object Error : SubscribeEvent
}

data class SubscribeUiState(
    val email: String = "",
    val isEmailValid: Boolean = true,
    val isSubmitting: Boolean = false,
    val subscribedEmail: String? = null,
) {
    val isAlreadySubscribed: Boolean
        get() = subscribedEmail != null && subscribedEmail.equals(email.trim(), ignoreCase = true)
}

class SubscribeViewModel(
    private val repository: TrendingRepository = TrendingRepository(),
    private val settings: SettingsManager = globalSettingsManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        run {
            val saved = settings.getSubscribedEmailSync()
            SubscribeUiState(email = saved.orEmpty(), subscribedEmail = saved)
        }
    )
    val uiState: StateFlow<SubscribeUiState> = _uiState.asStateFlow()

    private val _events = Channel<SubscribeEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun updateEmail(value: String) {
        val valid = value.isBlank() || EMAIL_REGEX.matches(value.trim())
        _uiState.update { it.copy(email = value, isEmailValid = valid) }
    }

    fun submit() {
        val state = _uiState.value
        val email = state.email.trim()
        if (email.isEmpty() || !EMAIL_REGEX.matches(email) || state.isSubmitting) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            val result = repository.subscribe(email, currentSource())
            result.fold(
                onSuccess = { resp ->
                    val status = SubscribeStatus.from(resp.status)
                    settings.setSubscribedEmail(email)
                    _uiState.update { it.copy(isSubmitting = false, subscribedEmail = email) }
                    _events.send(SubscribeEvent.Success(status))
                },
                onFailure = {
                    _uiState.update { it.copy(isSubmitting = false) }
                    _events.send(SubscribeEvent.Error)
                }
            )
        }
    }

    fun cancel() {
        val state = _uiState.value
        val email = (state.subscribedEmail ?: state.email).trim()
        if (email.isEmpty() || !EMAIL_REGEX.matches(email) || state.isSubmitting) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            val result = repository.cancelSubscribe(email)
            result.fold(
                onSuccess = { resp ->
                    val status = SubscribeStatus.from(resp.status)
                    settings.setSubscribedEmail(null)
                    _uiState.update { it.copy(isSubmitting = false, subscribedEmail = null) }
                    _events.send(SubscribeEvent.Success(status))
                },
                onFailure = {
                    _uiState.update { it.copy(isSubmitting = false) }
                    _events.send(SubscribeEvent.Error)
                }
            )
        }
    }

    private fun currentSource(): String =
        if (isIosPlatform()) "app-ios" else "app-android"

    companion object {
        private val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    }
}
