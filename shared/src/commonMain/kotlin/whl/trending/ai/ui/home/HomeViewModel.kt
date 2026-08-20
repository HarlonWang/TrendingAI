package whl.trending.ai.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import whl.trending.ai.core.analytics.AppEvent
import whl.trending.ai.core.analytics.ListFilter
import whl.trending.ai.core.analytics.Screen
import whl.trending.ai.core.analytics.track
import whl.trending.ai.data.local.SettingsManager
import whl.trending.ai.data.local.globalSettingsManager

/**
 * 首页 tab / 子源状态机。只管「现在停在哪」与相应的持久化、埋点，不碰各内容页的数据加载。
 *
 * - 选中 tab：初始值优先取 [SavedStateHandle]（进程被杀后恢复上次停留的 tab，对齐改造前
 *   rememberSaveable 的能力），其次取设置页的「默认首页」；会话内切换不回写设置。
 * - 选中子源：每次切换都回写设置，冷启动回到上次看的那个源（「记忆」而非「偏好」，
 *   见 handover 文档的产品决策）。
 * - 深链（通知点击等）经 [HomeTabRequest] 进来，在这里消费；深链切换与 back 降级
 *   （[backToHome]）都不记 tab_switched——那个事件只统计底栏点击。
 */
class HomeViewModel(
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val settingsManager: SettingsManager = globalSettingsManager,
    /** 事件上报出口，默认 [track]；注入是为了测试可断言「哪些路径记、哪些不记」 */
    private val track: (AppEvent) -> Unit = ::track,
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(
        HomeTab.fromNameOrDefault(
            savedStateHandle[SELECTED_TAB_KEY]
                ?: HomeTab.defaultFromName(settingsManager.currentDefaultHomeTab()).name
        )
    )
    val selectedTab: StateFlow<HomeTab> = _selectedTab.asStateFlow()

    private val _selectedSource = MutableStateFlow(
        TrendingSource.fromNameOrDefault(settingsManager.currentTrendingSource())
    )
    val selectedSource: StateFlow<TrendingSource> = _selectedSource.asStateFlow()

    init {
        // 组合树外的切 tab 请求（通知深链等）：置位状态可跨冷启动等到 VM 上线再消费。
        // Chat 是入口不是落点（见 HomeTab KDoc），请求它不合法，消费掉但不切换。
        viewModelScope.launch {
            HomeTabRequest.pending.collect { tab ->
                if (tab != null) {
                    if (tab != HomeTab.Chat) moveTo(tab)
                    HomeTabRequest.consume()
                }
            }
        }
    }

    /** 底栏点击：切换并记 tab_switched；重复点当前 tab 无操作。 */
    fun selectTab(tab: HomeTab) {
        if (tab == _selectedTab.value) return
        track(AppEvent.TabSwitched(tab.name.lowercase()))
        moveTo(tab)
    }

    /** 系统返回键在非 Home tab 上的降级：先回 Home，再退才真正退出（Material 底栏规范）。 */
    fun backToHome() {
        moveTo(HomeTab.Home)
    }

    fun selectSource(source: TrendingSource) {
        if (source == _selectedSource.value) return
        track(AppEvent.ListFiltered(ListFilter.SOURCE, source.name.lowercase()))
        _selectedSource.value = source
        settingsManager.setTrendingSource(source.name)
    }

    /**
     * 首页进设置页统一记一条事件，entry 区分来源。1.1 起只剩 topbar（顶栏常驻齿轮）——
     * 底栏「⋯」菜单随 Chat FAB 上位而删除，历史数据里的 entry=more 即那条已死路径。
     */
    fun onSettingsOpened(entry: String) {
        track(AppEvent.ScreenViewed(Screen.SETTINGS, from = entry))
    }

    private fun moveTo(tab: HomeTab) {
        _selectedTab.value = tab
        savedStateHandle[SELECTED_TAB_KEY] = tab.name
    }

    companion object {
        private const val SELECTED_TAB_KEY = "home_selected_tab"
    }
}
