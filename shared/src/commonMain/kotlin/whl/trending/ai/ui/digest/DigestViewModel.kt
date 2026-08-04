package whl.trending.ai.ui.digest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import whl.trending.ai.core.platform.trackEvent
import whl.trending.ai.data.local.SettingsManager
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.data.remote.TrendingApi

sealed interface DigestUiState {
    data object Loading : DigestUiState
    data class Ready(val markdown: String, val createdAt: String?) : DigestUiState

    /** 服务端明确无解读（insufficient/refused/老条目/未覆盖，统一不区分）；无重试语义 */
    data object Unavailable : DigestUiState

    /** 网络失败；可重试（只重试读取，不存在触发生成） */
    data object Error : DigestUiState
}

class DigestViewModel(
    private val page: DigestPage,
    private val api: TrendingApi = TrendingApi(),
    private val settingsManager: SettingsManager = globalSettingsManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow<DigestUiState>(DigestUiState.Loading)
    val uiState: StateFlow<DigestUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun retry() {
        _uiState.value = DigestUiState.Loading
        load()
    }

    private fun load() {
        viewModelScope.launch {
            try {
                // 语言跟「摘要语言」（summary_lang 口径），与列表内容语言一致；
                // 不要用 AI 请求的界面语言口径，否则列表摘要和解读页会出现两种语言
                val lang = settingsManager.currentContentLang()
                val response = api.fetchDigest(page.source, page.externalId, lang)
                _uiState.value = if (response.success && !response.markdown.isNullOrBlank()) {
                    DigestUiState.Ready(response.markdown, response.createdAt)
                } else {
                    trackEvent("digest_unavailable_shown", mapOf("source" to page.source))
                    DigestUiState.Unavailable
                }
            } catch (e: Exception) {
                _uiState.value = DigestUiState.Error
            }
        }
    }
}
