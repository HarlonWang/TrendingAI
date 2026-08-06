package whl.trending.ai.ui.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarExitDirection
import androidx.compose.material3.FloatingToolbarState
import androidx.compose.material3.rememberFloatingToolbarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

/**
 * 首页悬浮底栏「跟随滚动隐藏」的全部实现，由设置项开关
 * （`SettingsManager.hideBottomBarOnScroll`，默认关）决定要不要接入。
 *
 * 关闭时调用方传 null：[homeBarHideNestedScroll] 与 [homeBarHideOffset] 原样返回 receiver，
 * 首页那两处 modifier 链与接入前逐字节相同——没有 nestedScroll 分发、没有额外渲染层；
 * [rememberHomeFloatingBarHideState] 也不会被调用，state / behavior / effect 一概不进组合树。
 *
 * ## 为什么不直接把 behavior 交给 `HorizontalFloatingToolbar`
 *
 * M3 的 [FloatingToolbarDefaults.exitAlwaysScrollBehavior] 自带的位移
 * （`floatingScrollBehavior()`）把 `offsetLimit` 取成「胶囊到**其父布局**底边的距离」。
 * 官方 sample 里胶囊直接挂在铺满屏幕的 Box 上，那个距离恰好等于「滑出屏幕」；而我们的胶囊外面
 * 还包着一层固定高度的 `BoxWithConstraints`（[HomeFloatingBar] 照搬 Echo 的结构），外边距与
 * 导航栏 inset 都在这层之外，behavior 量不到——实测直接接上只滑下去胶囊自身高度，顶部还露出约一半。
 *
 * 所以这里只复用它的**曲线**（跟手 1:1、抛掷衰减、松手吸附到全显示/全隐藏），
 * 隐藏距离与位移自己算、自己施加。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
class HomeFloatingBarHideState internal constructor(
    internal val toolbarState: FloatingToolbarState,
    internal val connection: NestedScrollConnection,
) {
    /** 动画收回显示态。切 tab / 切子源这类「换了一屏内容」的时机调用。 */
    internal suspend fun reveal() {
        if (toolbarState.offset != 0f) {
            animate(
                initialValue = toolbarState.offset,
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ) { value, _ -> toolbarState.offset = value }
        }
    }
}

/**
 * 挂在包住各 tab 内容的容器上，让列表的滚动事件冒上来驱动底栏位移。
 * [state] 为 null（开关关闭）时原样返回，不产生任何 modifier 节点。
 */
fun Modifier.homeBarHideNestedScroll(state: HomeFloatingBarHideState?): Modifier =
    if (state == null) this else nestedScroll(state.connection)

/**
 * 挂在底栏上施加位移。读 offset 落在 draw 阶段，滚动全程不触发重组。
 * offset 为负（[FloatingToolbarState] 的约定：0 = 全显示，offsetLimit = 全隐藏），
 * 往屏幕外推要取反。[state] 为 null（开关关闭）时原样返回。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun Modifier.homeBarHideOffset(state: HomeFloatingBarHideState?): Modifier =
    if (state == null) this
    else graphicsLayer { translationY = -state.toolbarState.offset }

/**
 * 建立底栏滚动隐藏所需的状态。**只在开关打开时调用**——关闭时整个组合分支都不存在。
 *
 * @param hiddenDistance 胶囊从常驻位到完全滑出屏幕的距离：胶囊高 + 外边距 + 导航栏 inset。
 *   胶囊底边在「屏幕底 − (inset + 外边距)」，顶边再往上一个高度，推这么多顶边正好落到屏幕底沿。
 * @param revealKeys 这些值一变就把底栏收回显示态（切 tab、切子源）：不把上一屏滑到一半的隐藏量
 *   带过去，也免得新一屏内容不足一屏时底栏没机会自己回来。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun rememberHomeFloatingBarHideState(
    hiddenDistance: Dp,
    vararg revealKeys: Any?,
): HomeFloatingBarHideState {
    val toolbarState = rememberFloatingToolbarState()
    val behavior = FloatingToolbarDefaults.exitAlwaysScrollBehavior(
        exitDirection = FloatingToolbarExitDirection.Bottom,
        state = toolbarState,
    )
    val hiddenDistancePx = with(LocalDensity.current) { hiddenDistance.toPx() }

    SideEffect {
        toolbarState.offsetLimit = -hiddenDistancePx
        // offset 只在写入时 coerce：隐藏距离变了（转屏、手势导航切三键导航改的是 inset）
        // 得把已有位移夹回新区间，否则会一直停在按旧区间算出来的那个值上
        toolbarState.offset = toolbarState.offset.coerceAtLeast(-hiddenDistancePx)
    }

    val state = remember(toolbarState, behavior) {
        HomeFloatingBarHideState(toolbarState, behavior)
    }

    @Suppress("SpreadOperator")
    LaunchedEffect(state, *revealKeys) { state.reveal() }

    return state
}
