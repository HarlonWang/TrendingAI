package whl.trending.ai.ui.home

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 组合树外向首页发起的切 tab 请求（如通知点击深链到 Picks）。
 * 平台入口（MainActivity 等）调 [request]，HomeScreen 收到后切换并 [consume]。
 * 用 StateFlow 而非一次性事件：请求发出时 HomeScreen 可能尚未组合（冷启动），置位状态可等它上线再消费。
 */
object HomeTabRequest {
    private val _pending = MutableStateFlow<HomeTab?>(null)
    val pending: StateFlow<HomeTab?> = _pending.asStateFlow()

    fun request(tab: HomeTab) {
        _pending.value = tab
    }

    fun consume() {
        _pending.value = null
    }
}
