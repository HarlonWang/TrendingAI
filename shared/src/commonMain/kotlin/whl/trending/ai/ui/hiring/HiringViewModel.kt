package whl.trending.ai.ui.hiring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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

/**
 * 筛选维度。与 [whl.trending.ai.core.analytics.ListFilter] 一一对应，改这里记得同步那边。
 *
 * 只留两维是刻意的：职能是「这是不是我干的活」，远程形态是「我能不能干」，两刀覆盖绝大多数意图。
 * 地域开放度与雇佣类型退出筛选（前者 restricted+unspecified 占 94%，筛了不解决问题；后者全职占
 * 六成），但事实仍在卡片与详情里明示——退出的是筛选，不是信息。
 */
enum class HiringFilterDim { ROLE_CATEGORY, REMOTE_KIND }

sealed interface HiringUiState {
    data object Loading : HiringUiState
    data class Ready(
        val month: String,
        val months: List<String>,
        /** 当月主帖 id（HN story）。来源行据此跳原帖；后端没给就降级成不可点的纯文字标注 */
        val storyId: String,
        val lastSyncedAt: String?,
        /** 当期全部岗位，未经筛选。筛选是纯展示层的事，不动这份数据 */
        val all: List<HiringPost>,
        val selected: Map<HiringFilterDim, Set<String>>,
    ) : HiringUiState {
        /** 命中当前筛选的岗位。多维之间取交集，同维多选取并集 */
        val filtered: List<HiringPost> get() = all.filter { p ->
            selected.all { (dim, vals) -> vals.isEmpty() || values(p, dim).any { it in vals } }
        }

        /**
         * 某维度各取值的计数，**在「其余维度已生效」的子集上算**——这样点了一个条件后，
         * 其他条件的数字会跟着变，用户能看到「再叠一个还剩多少」。
         * 服务端返回的 facets 只是首屏的初值，联动计数必须本地算（数据全在手，零往返）。
         *
         * ⚠️ 多值维度（职能）里一条帖子会在它命中的每个取值下各计一次，
         * **所以各项之和大于岗位总数**，界面文案不能让用户以为这些数能加成总条数。
         */
        fun counts(dim: HiringFilterDim): Map<String, Int> {
            val base = all.filter { p ->
                selected.all { (d, vals) ->
                    d == dim || vals.isEmpty() || values(p, d).any { it in vals }
                }
            }
            return base.flatMap { values(it, dim) }.groupingBy { it }.eachCount()
        }

        val isFiltering: Boolean get() = selected.values.any { it.isNotEmpty() }
    }

    data object Unavailable : HiringUiState
    data object Error : HiringUiState
}

/** 一条岗位在某维度上的取值。职能是多值（一帖多岗），远程形态是单值，统一成列表处理 */
private fun values(p: HiringPost, dim: HiringFilterDim): List<String> = when (dim) {
    HiringFilterDim.ROLE_CATEGORY -> p.roleCategories
    HiringFilterDim.REMOTE_KIND -> listOf(p.remoteKind)
}

class HiringViewModel(
    private val initialMonth: String?,
    private val api: TrendingApi = TrendingApi(),
    private val settingsManager: SettingsManager = globalSettingsManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow<HiringUiState>(HiringUiState.Loading)
    val uiState: StateFlow<HiringUiState> = _uiState.asStateFlow()

    /**
     * 当前请求的月份，**不从 uiState 里反推**。
     * uiState 会被 Loading 冲掉，取消息源放在那里会让「先置 Loading 再读月份」这种手滑
     * 静默退化成「永远回到初始月份」——本类曾在 retry() 里正是这么错的。
     * switchMonth 更新它，retry 与语言重载都读它，三条路径共用同一个真相来源。
     */
    private var requestedMonth: String? = initialMonth

    /** 进行中的加载。新请求前取消旧的，防止慢的旧响应后到、用旧月份/旧语言的数据覆盖新状态 */
    private var loadJob: Job? = null

    init {
        load()

        // 摘要语言切换后重载（对齐 DigestViewModel / FeedViewModel 的既有模式）：
        // viewModel 实例生命周期长于页面，init 里的语言只是创建时刻的快照。
        // 只有 title/summary 跟语言走，事实字段与筛选结果不受影响。
        viewModelScope.launch {
            settingsManager.summaryLanguage.drop(1).collect { load() }
        }
    }

    fun retry() = load()

    /** 往期切换。切月清空筛选——上一期选中的取值在新一期未必存在，留着会得到一个空列表且不知为何 */
    fun switchMonth(month: String) {
        if (requestedMonth == month && _uiState.value is HiringUiState.Ready) return
        requestedMonth = month
        load()
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

    private fun load() {
        loadJob?.cancel()
        _uiState.value = HiringUiState.Loading
        loadJob = viewModelScope.launch {
            try {
                // 语言跟「摘要语言」口径，与列表/解读页一致
                val lang = settingsManager.currentContentLang()
                val r = api.fetchHiring(requestedMonth, lang)
                _uiState.value = when {
                    r.success -> HiringUiState.Ready(
                        month = r.month,
                        months = r.months,
                        storyId = r.storyId,
                        lastSyncedAt = r.lastSyncedAt,
                        all = r.posts,
                        // 进入页面一律无筛选态，且不记忆偏好——不替用户预设任何条件
                        selected = emptyMap(),
                    )
                    // 只有后端明确说「这一期不存在」才是产品状态；其余（含仍返回 JSON 的 5xx）
                    // 一律当故障处理，否则会把基础设施问题伪装成「本期尚未发布」，
                    // 而那个态没有重试出口，用户直接卡死、读数也分不清两者
                    r.code == CODE_ROUND_UNAVAILABLE -> HiringUiState.Unavailable
                    else -> HiringUiState.Error
                }
            } catch (e: CancellationException) {
                // 取消是新请求挤掉旧请求，不是失败——吞掉会让切月/切语言瞬间闪一下 Error
                throw e
            } catch (e: Exception) {
                _uiState.value = HiringUiState.Error
            }
        }
    }

    private companion object {
        const val CODE_ROUND_UNAVAILABLE = "hiring_round_unavailable"
    }
}
