package whl.trending.ai.ui.main

import whl.trending.ai.data.model.TrendingRepo
import whl.trending.ai.data.repository.TrendingRepository
import whl.trending.ai.core.DateTimeUtils
import whl.trending.ai.data.local.SettingsManager
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.core.platform.getSystemLanguage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val repos: List<TrendingRepo> = emptyList(),
    val since: String = "",
    val capturedAt: String = "",
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val selectedPeriod: String = "daily",
    val selectedLanguage: String = "all",
    val selectedDate: String? = null,
    val selectedBatch: String? = null,
    val error: String? = null
)

class MainViewModel(
    private val repository: TrendingRepository = TrendingRepository(),
    private val settingsManager: SettingsManager = globalSettingsManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var fetchJob: Job? = null

    init {
        fetchData()
        
        viewModelScope.launch {
            // drop(1) 丢弃首次初始化的当前值，只监听真正发生的设置修改，避免初始化时重复调用 fetchData
            settingsManager.appLanguage.drop(1).collect {
                fetchData(isRefresh = true)
            }
        }
    }

    fun fetchData(isRefresh: Boolean = false) {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            if (isRefresh) {
                _uiState.update { it.copy(isRefreshing = true, error = null) }
                delay(500)
            } else {
                _uiState.update { it.copy(isLoading = true, error = null) }
            }

            try {
                val currentAppLanguage = settingsManager.appLanguage.first()
                val summaryLang = currentAppLanguage.isoCode ?: getSystemLanguage()
                
                val response = repository.getTrending(
                    _uiState.value.selectedPeriod,
                    _uiState.value.selectedLanguage,
                    summaryLang,
                    _uiState.value.selectedDate,
                    _uiState.value.selectedBatch
                )
                _uiState.update { 
                    it.copy(
                        repos = response.data,
                        since = response.metadata.since,
                        capturedAt = DateTimeUtils.formatToLocalTime(response.metadata.capturedAt),
                        isLoading = false,
                        isRefreshing = false,
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

    fun updateFilter(period: String, language: String) {
        if (_uiState.value.selectedPeriod == period &&
            _uiState.value.selectedLanguage == language) return

        _uiState.update {
            it.copy(
                selectedPeriod = period,
                selectedLanguage = language
            )
        }
        fetchData()
    }

    fun updateHistoryFilter(date: String?, batch: String?) {
        if (_uiState.value.selectedDate == date && 
            _uiState.value.selectedBatch == batch) return
        
        _uiState.update { 
            it.copy(
                selectedDate = date,
                selectedBatch = batch
            )
        }
        fetchData()
    }
}
