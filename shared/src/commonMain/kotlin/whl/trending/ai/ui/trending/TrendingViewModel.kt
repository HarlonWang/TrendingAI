package whl.trending.ai.ui.trending

import whl.trending.ai.auth.RepoStarService
import whl.trending.ai.data.model.TrendingRepo
import whl.trending.ai.data.model.TrendingResponse
import whl.trending.ai.data.repository.TrendingRepository
import whl.trending.ai.core.platform.trackEvent
import whl.trending.ai.data.local.LastDataCache
import whl.trending.ai.data.local.SettingsManager
import whl.trending.ai.data.local.globalLastDataCache
import whl.trending.ai.data.local.globalSettingsManager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TrendingUiState(
    val repos: List<TrendingRepo> = emptyList(),
    val since: String = "",
    /** 本批数据的抓取时刻，**UTC 原串**——展示侧（抓取时机条）自行换算本地时区并决定格式 */
    val capturedAt: String = "",
    /** 本批数据所属批次：'am' / 'pm'，随抓取时机条一起显示 */
    val currentBatch: String = "",
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val selectedPeriod: String = "daily",
    val selectedLanguage: String = "all",
    val selectedDate: String? = null,
    val selectedBatch: String? = null,
    /** 「只看 New」开关：仅对 daily 全语言榜有效，开启时只展示 isNew 的项目 */
    val newOnly: Boolean = false,
    /** 筛选/历史两个底部弹窗的可见性。触发按钮在首页顶栏、弹窗在本页，状态放这里两边共享 */
    val showFilterSheet: Boolean = false,
    val showHistorySheet: Boolean = false,
    val error: String? = null
)

class TrendingViewModel(
    private val repository: TrendingRepository = TrendingRepository(),
    private val settingsManager: SettingsManager = globalSettingsManager,
    private val starService: RepoStarService = RepoStarService.shared,
    private val cache: LastDataCache = globalLastDataCache,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TrendingUiState())
    val uiState: StateFlow<TrendingUiState> = _uiState.asStateFlow()

    /** star 操作结果一次性事件：UI 收到后弹 Snackbar（成功/失败/需登录） */
    private val _starEvents = MutableSharedFlow<RepoStarService.Result>(extraBufferCapacity = 1)
    val starEvents: SharedFlow<RepoStarService.Result> = _starEvents.asSharedFlow()

    private var fetchJob: Job? = null

    init {
        // 「只看 New」开关即设置：冷启动恢复上次显式切换的状态（toggleNewOnly 回写）。
        // 初始视图就是该过滤唯一有效的 daily 全语言榜，直接置位即可。
        if (settingsManager.currentTrendingNewOnly()) {
            _uiState.update { it.copy(newOnly = true) }
        }
        loadWithCache()

        viewModelScope.launch {
            // drop(1) 丢弃首次初始化的当前值，只监听真正发生的设置修改，避免初始化时重复调用 fetchData
            settingsManager.summaryLanguage.drop(1).collect {
                fetchData(isRefresh = true)
            }
        }
    }

    private fun cacheKey(lang: String) = "trending_default_$lang"

    /** 只缓存默认视图：daily + 全语言 + 无历史日期/批次（冷启动看到的就是它） */
    private fun isDefaultView(period: String, language: String, date: String?, batch: String?): Boolean =
        period == "daily" && language == "all" && date == null && batch == null

    /** SWR：默认视图有缓存先展示 + 顶部指示器自动刷新（不加防闪烁延迟）；无缓存走骨架屏 */
    private fun loadWithCache() {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            val cached = cache.get<TrendingResponse>(cacheKey(settingsManager.currentContentLang()))
            if (cached != null) {
                _uiState.update {
                    it.copy(
                        repos = cached.data,
                        since = cached.metadata.since,
                        capturedAt = cached.metadata.capturedAt,
                        currentBatch = cached.metadata.batch,
                        isLoading = false,
                        isRefreshing = true,
                    )
                }
            }
            fetch()
        }
    }

    fun fetchData(isRefresh: Boolean = false) {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            if (isRefresh) {
                _uiState.update { it.copy(isRefreshing = true, error = null) }
                // 防指示器闪烁延迟，仅手动下拉/设置变更触发的刷新需要
                delay(500)
            } else {
                _uiState.update { it.copy(isLoading = true, error = null) }
            }
            fetch()
        }
    }

    private suspend fun fetch() {
        // 请求参数在发起时定格为局部值，成功后据此判断是否写缓存，避免请求期间切筛选造成错写
        val period = _uiState.value.selectedPeriod
        val language = _uiState.value.selectedLanguage
        val date = _uiState.value.selectedDate
        val batch = _uiState.value.selectedBatch
        try {
            val summaryLang = settingsManager.currentContentLang()

            val response = repository.getTrending(period, language, summaryLang, date, batch)
            if (isDefaultView(period, language, date, batch)) {
                cache.put(cacheKey(summaryLang), response)
            }
            _uiState.update {
                it.copy(
                    repos = response.data,
                    since = response.metadata.since,
                    capturedAt = response.metadata.capturedAt,
                    currentBatch = response.metadata.batch,
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
                    // 有内容（缓存或旧数据）时刷新失败静默保留，避免整页错误盖掉可用内容
                    error = if (it.repos.isEmpty()) e.message ?: "Unknown Error" else null
                )
            }
        }
    }

    /**
     * 列表页 star：仅做「点 star」（不展示已 star 状态，避免逐项查询）。GitHub PUT 幂等，
     * 已 star 再点也安全。结果通过 [starEvents] 通知 UI。
     */
    fun starRepo(repo: TrendingRepo) {
        viewModelScope.launch {
            val result = starService.star(repo.author, repo.repoName)
            _starEvents.tryEmit(result)
            if (result == RepoStarService.Result.STARRED) {
                trackEvent(
                    "repo_star",
                    mapOf("source" to "github", "from" to "list")
                )
            }
        }
    }

    fun updateFilter(period: String, language: String) {
        if (_uiState.value.selectedPeriod == period &&
            _uiState.value.selectedLanguage == language) return

        // New-only 仅 daily 全语言榜有效：切到其他视图时自动关掉
        val keepNewOnly = _uiState.value.newOnly && period == "daily" && language == "all"
        _uiState.update {
            it.copy(
                selectedPeriod = period,
                selectedLanguage = language,
                newOnly = keepNewOnly
            )
        }
        fetchData()
    }

    /**
     * 切换「只看 New」。开启时把视图 snap 到 daily 全语言榜（isNew 仅此视图有效）；
     * 已在该视图时仅改本地过滤状态，不触发网络请求。
     * 开关即设置：显式切换立即持久化，冷启动恢复；[updateFilter] 的视图约束自动关闭不回写。
     */
    fun toggleNewOnly() {
        val turnOn = !_uiState.value.newOnly
        val effPeriod = if (turnOn) "daily" else _uiState.value.selectedPeriod
        val effLang = if (turnOn) "all" else _uiState.value.selectedLanguage
        val needFetch = _uiState.value.selectedPeriod != effPeriod ||
            _uiState.value.selectedLanguage != effLang

        _uiState.update {
            it.copy(selectedPeriod = effPeriod, selectedLanguage = effLang, newOnly = turnOn)
        }
        settingsManager.setTrendingNewOnly(turnOn)
        trackEvent("trending_new_only", mapOf("on" to turnOn))
        if (needFetch) fetchData()
    }

    fun setFilterSheetVisible(visible: Boolean) {
        _uiState.update { it.copy(showFilterSheet = visible) }
    }

    fun setHistorySheetVisible(visible: Boolean) {
        _uiState.update { it.copy(showHistorySheet = visible) }
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
