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
 * 沉浸式浏览的统一隐藏状态：一条 [NestedScrollConnection] 产出隐藏进度
 * [ImmersiveState.progress]，各栏按**自己的行程**消费——同一进度 × 不同行程，速率差
 * 即视差：子 tab 行行程最长（1:1 跟手）、顶栏次之、底栏向下——三栏 + 状态栏 scrim
 * 四个消费点全部挂在 HomeScreen。
 *
 * 由设置项开关（`SettingsManager.immersiveBrowsing`，默认关）决定要不要接入。
 * 关闭时调用方传 null：[immersiveNestedScroll] 与 [immersiveExit] 原样返回 receiver，
 * 首页的 modifier 链与接入前逐字节相同——没有 nestedScroll 分发、没有额外渲染层；
 * [rememberImmersiveState] 也不会被调用，state / behavior / effect 一概不进组合树。
 *
 * 只复用 M3 [FloatingToolbarDefaults.exitAlwaysScrollBehavior] 的**曲线**（跟手 1:1、
 * 抛掷衰减、松手吸附到全显示/全隐藏），手势行程与各栏位移自己算、自己施加——它自带的
 * 位移量不到我们的真实行程，且只能驱动工具栏自己，管不到顶栏和子 tab 行。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
class ImmersiveState internal constructor(
    internal val toolbarState: FloatingToolbarState,
    internal val connection: NestedScrollConnection,
) {
    /**
     * 隐藏进度：0 = 全显示，1 = 全隐藏。
     * **只应在 draw 阶段（graphicsLayer lambda）读取**——读进 composition 会把滚动
     * 变成逐帧重组，这是本机制的性能红线。
     */
    internal val progress: Float
        get() {
            val limit = toolbarState.offsetLimit
            // offsetLimit 初始为 -Float.MAX_VALUE（SideEffect 首帧后才写入真值），
            // 此时 offset/limit ≈ 0，天然落在「全显示」，无首帧跳变；仅防 0 除。
            return if (limit == 0f) 0f else (toolbarState.offset / limit).coerceIn(0f, 1f)
        }

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

/** 沉浸时该栏从哪条屏幕边缘退场，决定位移方向。 */
enum class ImmersiveEdge { Top, Bottom }

/**
 * 挂在包住各 tab 内容的容器上，让列表的滚动事件冒上来驱动隐藏进度。
 * [state] 为 null（开关关闭）时原样返回，不产生任何 modifier 节点。
 */
fun Modifier.immersiveNestedScroll(state: ImmersiveState?): Modifier =
    if (state == null) this else nestedScroll(state.connection)

/**
 * 挂在要退场的栏上施加位移：[travel] 是该栏从常驻位到完全出屏的距离，[edge] 决定方向。
 * 读 progress 落在 draw 阶段，滚动全程不触发重组。[state] 为 null（开关关闭）时原样返回。
 */
fun Modifier.immersiveExit(state: ImmersiveState?, edge: ImmersiveEdge, travel: Dp): Modifier =
    if (state == null) this
    else graphicsLayer {
        val ty = state.progress * travel.toPx()
        translationY = if (edge == ImmersiveEdge.Top) -ty else ty
    }

/**
 * 建立沉浸式所需的状态。**只在开关打开时调用**——关闭时整个组合分支都不存在。
 *
 * @param gestureTravel 手势行程：滚动累计这么多距离后进度走满。取**所有栏中最长的行程**
 *   （Home 的子 tab 行：状态栏 + 顶栏 + tab 行），让最长行程那个元素 1:1 跟手，
 *   其余栏按行程比例减速——这正是视差的来源。
 * @param revealKeys 这些值一变就收回显示态（切 tab、切子源）：不把上一屏滑到一半的
 *   隐藏量带过去，也免得新一屏内容不足一屏时各栏没机会自己回来。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun rememberImmersiveState(
    gestureTravel: Dp,
    vararg revealKeys: Any?,
): ImmersiveState {
    val toolbarState = rememberFloatingToolbarState()
    val behavior = FloatingToolbarDefaults.exitAlwaysScrollBehavior(
        exitDirection = FloatingToolbarExitDirection.Bottom,
        state = toolbarState,
    )
    val gestureTravelPx = with(LocalDensity.current) { gestureTravel.toPx() }

    SideEffect {
        toolbarState.offsetLimit = -gestureTravelPx
        // offset 只在写入时 coerce：行程变了（转屏、手势导航切三键导航改的是 inset）
        // 得把已有位移夹回新区间，否则会一直停在按旧区间算出来的那个值上
        toolbarState.offset = toolbarState.offset.coerceAtLeast(-gestureTravelPx)
    }

    val state = remember(toolbarState, behavior) {
        ImmersiveState(toolbarState, behavior)
    }

    @Suppress("SpreadOperator")
    LaunchedEffect(state, *revealKeys) { state.reveal() }

    return state
}
