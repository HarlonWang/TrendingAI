package whl.trending.ai.ui.subscribe

import whl.trending.ai.core.isValidEmail
import whl.trending.ai.core.platform.isIosPlatform
import whl.trending.ai.core.platform.trackEvent
import whl.trending.ai.data.local.SettingsManager
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.data.model.SubscribeStatus
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
    private val repository: TrendingRepository = TrendingRepository.shared,
    private val settings: SettingsManager = globalSettingsManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        run {
            val saved = settings.currentSubscribedEmail()
            SubscribeUiState(email = saved.orEmpty(), subscribedEmail = saved)
        }
    )
    val uiState: StateFlow<SubscribeUiState> = _uiState.asStateFlow()

    private val _events = Channel<SubscribeEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun updateEmail(value: String) {
        val valid = value.isBlank() || isValidEmail(value)
        _uiState.update { it.copy(email = value, isEmailValid = valid) }
    }

    fun submit() {
        val state = _uiState.value
        val email = state.email.trim()
        if (email.isEmpty() || !isValidEmail(email) || state.isSubmitting) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            // 订阅语言跟随摘要语言设置（与摘要请求同口径），后端按 zh/en 决定邮件语言，非法值兜底英文
            val lang = settings.currentContentLang()
            val result = repository.subscribe(email, currentSource(), lang)
            result.fold(
                onSuccess = { resp ->
                    val status = SubscribeStatus.from(resp.status)
                    trackEvent("subscribe_submit", mapOf("result" to "success", "lang" to lang, "status" to status.name.lowercase()))
                    settings.setSubscribedEmail(email)
                    _uiState.update { it.copy(isSubmitting = false, subscribedEmail = email) }
                    _events.send(SubscribeEvent.Success(status))
                },
                onFailure = {
                    trackEvent("subscribe_submit", mapOf("result" to "error", "lang" to lang))
                    _uiState.update { it.copy(isSubmitting = false) }
                    _events.send(SubscribeEvent.Error)
                }
            )
        }
    }

    fun cancel() {
        val state = _uiState.value
        val email = (state.subscribedEmail ?: state.email).trim()
        if (email.isEmpty() || !isValidEmail(email) || state.isSubmitting) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            val result = repository.cancelSubscribe(email)
            result.fold(
                onSuccess = { resp ->
                    val status = SubscribeStatus.from(resp.status)
                    trackEvent("subscribe_cancel", mapOf("result" to "success"))
                    settings.setSubscribedEmail(null)
                    _uiState.update { it.copy(isSubmitting = false, subscribedEmail = null) }
                    _events.send(SubscribeEvent.Success(status))
                },
                onFailure = {
                    trackEvent("subscribe_cancel", mapOf("result" to "error"))
                    _uiState.update { it.copy(isSubmitting = false) }
                    _events.send(SubscribeEvent.Error)
                }
            )
        }
    }

    private fun currentSource(): String =
        if (isIosPlatform()) "app-ios" else "app-android"
}
