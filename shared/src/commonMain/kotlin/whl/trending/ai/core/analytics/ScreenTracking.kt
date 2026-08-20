package whl.trending.ai.core.analytics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import whl.trending.ai.core.Route

/**
 * 最后一次到达的页面，只用来填下一条事件的 `from`。
 *
 * 单线程可见即可——两个上报源都在 Compose 主线程上跑。
 */
private var lastScreen: Screen? = null

/**
 * 页面浏览上报的**唯一**入口，两个源共用（见 [TrackRouteScreenViews] 与 `HomeScreen` 的 tab 源）。
 * 共用同一个 [lastScreen] 是 `from` 能跨源正确的原因：从首页某个 tab 进二级页时，
 * `from` 拿到的是那个 tab 而不是「首页」这个容器。
 *
 * **不在这里去重**——去重由各源按自己的身份做（路由按 key、tab 按枚举）。
 * 放这里会把 README(a) → README(b) 这类同 screen 不同内容的跳转吃掉。
 */
internal fun trackScreenView(screen: Screen) {
    track(AppEvent.ScreenViewed(screen, from = lastScreen))
    lastScreen = screen
}

/**
 * 浮层形态的页面（目前只有登录浮层）。上报但**不推进** [lastScreen]：
 * 浮层不改变「用户在哪一页」，关掉之后下一次跳转的 `from` 仍应是它底下那一页。
 */
internal fun trackOverlayScreenView(screen: Screen) {
    track(AppEvent.ScreenViewed(screen, from = lastScreen))
}

/**
 * 路由源：栈顶变化即页面到达，覆盖全部二级路由。挂一次，全 app 生效。
 *
 * 按**路由 key**（data class 相等性）去重而不是按 `screen`，所以 `RepoDetail(a)` → `RepoDetail(b)`
 * 算两次浏览、重复 push 同一 key 只算一次。返回（pop）会让栈顶回到上一个 key，
 * 于是自然产生一条——与 GA / Firebase 的 `screen_view` 口径一致，是有意为之。
 *
 * 旋转屏幕会重建 Activity，backStack 是 `remember` 故回落到首页，这里会如实再报一条首页。
 * 不做跨重建去重：用户确实被扔回了首页，那一条不是噪音，是那个既有导航缺陷的忠实反映。
 */
@Composable
fun TrackRouteScreenViews(backStack: List<Route>) {
    LaunchedEffect(Unit) {
        snapshotFlow { backStack.lastOrNull() }
            .distinctUntilChanged()
            .collect { route -> route?.screen?.let(::trackScreenView) }
    }
}
