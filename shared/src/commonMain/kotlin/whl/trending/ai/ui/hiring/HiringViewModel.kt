package whl.trending.ai.ui.hiring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import whl.trending.ai.data.local.SettingsManager
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.data.model.HiringPost
import whl.trending.ai.data.remote.TrendingApi

/** 筛选维度。与 [whl.trending.ai.core.analytics.ListFilter] 一一对应，改这里记得同步那边 */
enum class HiringFilterDim { REGION_SCOPE, REMOTE_KIND, EMPLOYMENT }

sealed interface HiringUiState {
    data object Loading : HiringUiState
    data class Ready(
        val month: String,
        val months: List<String>,
        val lastSyncedAt: String?,
        /** 当期全部岗位，未经筛选。筛选是纯展示层的事，不动这份数据 */
        val all: List<HiringPost>,
        val selected: Map<HiringFilterDim, Set<String>>,
    ) : HiringUiState {
        /** 命中当前筛选的岗位。多维之间取交集，同维多选取并集 */
        val filtered: List<HiringPost> get() = all.filter { p -> selected.all { (dim, vals) ->
            vals.isEmpty() || value(p, dim) in vals
        } }

        /**
         * 某维度各取值的计数，**在「其余维度已生效」的子集上算**——这样点了一个条件后，
         * 其他条件的数字会跟着变，用户能看到「再叠一个还剩多少」。
         * 服务端返回的 facets 只是首屏的初值，联动计数必须本地算（数据全在手，零往返）。
         */
        fun counts(dim: HiringFilterDim): Map<String, Int> {
            val base = all.filter { p -> selected.all { (d, vals) ->
                d == dim || vals.isEmpty() || value(p, d) in vals
            } }
            return base.groupingBy { value(p = it, dim = dim) }.eachCount()
        }

        val isFiltering: Boolean get() = selected.values.any { it.isNotEmpty() }
    }

    data object Unavailable : HiringUiState
    data object Error : HiringUiState
}

private fun value(p: HiringPost, dim: HiringFilterDim): String = when (dim) {
    HiringFilterDim.REGION_SCOPE -> p.regionScope
    HiringFilterDim.REMOTE_KIND -> p.remoteKind
    HiringFilterDim.EMPLOYMENT -> p.employment ?: "unspecified"
}

class HiringViewModel(
    private val initialMonth: String?,
    private val api: TrendingApi = TrendingApi(),
    private val settingsManager: SettingsManager = globalSettingsManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow<HiringUiState>(HiringUiState.Loading)
    val uiState: StateFlow<HiringUiState> = _uiState.asStateFlow()

    init {
        load(initialMonth)

        // 摘要语言切换后重载（对齐 DigestViewModel / FeedViewModel 的既有模式）：
        // viewModel 实例生命周期长于页面，init 里的语言只是创建时刻的快照。
        // 只有 title/summary 跟语言走，事实字段与筛选结果不受影响。
        viewModelScope.launch {
            settingsManager.summaryLanguage.drop(1).collect {
                val month = (_uiState.value as? HiringUiState.Ready)?.month ?: initialMonth
                _uiState.value = HiringUiState.Loading
                load(month)
            }
        }
    }

    fun retry() {
        _uiState.value = HiringUiState.Loading
        load((_uiState.value as? HiringUiState.Ready)?.month ?: initialMonth)
    }

    /** 往期切换。切月清空筛选——上一期选中的取值在新一期未必存在，留着会得到一个空列表且不知为何 */
    fun switchMonth(month: String) {
        if ((_uiState.value as? HiringUiState.Ready)?.month == month) return
        _uiState.value = HiringUiState.Loading
        load(month)
    }

    /** 单个取值的选中/取消。同维多选取并集 */
    fun toggle(dim: HiringFilterDim, value: String) {
        _uiState.update { s ->
            if (s !is HiringUiState.Ready) return@update s
            val cur = s.selected[dim].orEmpty()
            val next = if (value in cur) cur - value else cur + value
            s.copy(selected = s.selected + (dim to next))
        }
    }

    fun clearFilters() {
        _uiState.update { s ->
            if (s !is HiringUiState.Ready) s else s.copy(selected = emptyMap())
        }
    }

    private fun load(month: String?) {
        viewModelScope.launch {
            try {
                // 语言跟「摘要语言」口径，与列表/解读页一致
                val lang = settingsManager.currentContentLang()
                val r = api.fetchHiring(month, lang)
                _uiState.value = if (r.success && r.posts.isNotEmpty()) {
                    HiringUiState.Ready(
                        month = r.month,
                        months = r.months,
                        lastSyncedAt = r.lastSyncedAt,
                        all = r.posts,
                        // 进入页面一律无筛选态，且不记忆偏好——不替用户预设任何条件
                        selected = emptyMap(),
                    )
                } else {
                    HiringUiState.Unavailable
                }
            } catch (e: Exception) {
                _uiState.value = HiringUiState.Error
            }
        }
    }
}
