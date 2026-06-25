package whl.trending.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import whl.trending.ai.core.platform.trackEvent
import whl.trending.chat.engine.ChatException
import whl.trending.chat.engine.byok.ByokChatEngine
import whl.trending.chat.engine.byok.ByokConfig
import whl.trending.chat.engine.byok.ByokConfigStore
import whl.trending.chat.engine.byok.ByokProvider
import whl.trending.chat.engine.byok.analyticsName
import whl.trending.chat.model.ChatError

/** “拉取模型”的状态机。 */
sealed interface FetchState {
    data object Idle : FetchState
    data object Loading : FetchState
    data class Success(val count: Int) : FetchState
    data class Failed(val error: ChatError) : FetchState
}

data class ByokSettingsUiState(
    val enabled: Boolean = false,
    val provider: ByokProvider = ByokProvider.OPENAI_COMPATIBLE,
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val models: List<String> = emptyList(),
    val manualModel: Boolean = false,
    val fetch: FetchState = FetchState.Idle,
) {
    val canFetch: Boolean get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && fetch != FetchState.Loading
}

/**
 * BYOK 设置页 ViewModel。字段改动即时持久化（非敏感走 SettingsManager，apiKey 走加密存储），
 * 故退出即生效，无需"保存"按钮。
 */
class ByokSettingsViewModel : ViewModel() {

    private val _uiState: MutableStateFlow<ByokSettingsUiState>
    val uiState: StateFlow<ByokSettingsUiState>

    init {
        val cfg = ByokConfigStore.current()
        _uiState = MutableStateFlow(
            ByokSettingsUiState(
                enabled = cfg.enabled,
                provider = cfg.provider,
                baseUrl = cfg.baseUrl.ifBlank { defaultBaseUrl(cfg.provider) },
                apiKey = cfg.apiKey,
                model = cfg.model,
                models = if (cfg.model.isNotBlank()) listOf(cfg.model) else emptyList(),
                manualModel = cfg.model.isNotBlank() && cfg.baseUrl.isNotBlank(),
            ),
        )
        uiState = _uiState.asStateFlow()
    }

    fun setEnabled(value: Boolean) {
        _uiState.update { it.copy(enabled = value) }
        ByokConfigStore.setEnabled(value)
        trackEvent("byok_enabled_changed", mapOf("enabled" to value))
    }

    fun setProvider(provider: ByokProvider) {
        _uiState.update { state ->
            // 切换 provider：模型列表失效；baseUrl 若是另一家默认值则换成本家默认值
            val newBase = if (state.baseUrl.isBlank() || state.baseUrl == defaultBaseUrl(state.provider)) {
                defaultBaseUrl(provider)
            } else {
                state.baseUrl
            }
            state.copy(provider = provider, baseUrl = newBase, models = emptyList(), fetch = FetchState.Idle)
        }
        ByokConfigStore.setProvider(provider)
        ByokConfigStore.setBaseUrl(_uiState.value.baseUrl)
    }

    fun setBaseUrl(value: String) {
        _uiState.update { it.copy(baseUrl = value, fetch = FetchState.Idle) }
        ByokConfigStore.setBaseUrl(value)
    }

    fun setApiKey(value: String) {
        _uiState.update { it.copy(apiKey = value, fetch = FetchState.Idle) }
        ByokConfigStore.setApiKey(value)
    }

    fun setModel(value: String) {
        _uiState.update { it.copy(model = value) }
        ByokConfigStore.setModel(value)
    }

    fun toggleManualModel(manual: Boolean) {
        _uiState.update { it.copy(manualModel = manual) }
    }

    fun fetchModels() {
        val state = _uiState.value
        if (!state.canFetch) return
        _uiState.update { it.copy(fetch = FetchState.Loading) }
        val providerName = state.provider.analyticsName()
        trackEvent("byok_fetch_models", mapOf("provider" to providerName))
        viewModelScope.launch {
            try {
                val cfg = ByokConfig(
                    enabled = state.enabled,
                    provider = state.provider,
                    baseUrl = state.baseUrl.trim(),
                    apiKey = state.apiKey.trim(),
                    model = state.model,
                )
                val models = ByokChatEngine.fetchModels(cfg)
                _uiState.update {
                    it.copy(
                        models = models,
                        manualModel = models.isEmpty(),
                        fetch = FetchState.Success(models.size),
                    )
                }
                trackEvent("byok_fetch_result", mapOf("provider" to providerName, "success" to true))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val error = (e as? ChatException)?.error ?: ChatError(whl.trending.chat.model.ChatErrorCategory.UNKNOWN)
                _uiState.update { it.copy(fetch = FetchState.Failed(error), manualModel = true) }
                trackEvent(
                    "byok_fetch_result",
                    mapOf(
                        "provider" to providerName,
                        "success" to false,
                        "error_category" to error.category.name.lowercase(),
                    ),
                )
            }
        }
    }

    private fun defaultBaseUrl(provider: ByokProvider): String = when (provider) {
        ByokProvider.OPENAI_COMPATIBLE -> "https://api.openai.com/v1"
        ByokProvider.ANTHROPIC -> "https://api.anthropic.com"
    }
}
