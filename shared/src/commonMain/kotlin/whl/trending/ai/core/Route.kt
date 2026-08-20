package whl.trending.ai.core

import whl.trending.ai.core.analytics.Screen

/**
 * 导航目的地。实现它是进 backStack 的**唯一**方式（栈的元素类型就是它），
 * 于是「新增页面忘了埋点」变成编译错误——页面浏览事件由导航层自动产生，
 * 页面自己一行 track 都不写（见 `core/analytics/ScreenTracking.kt`）。
 *
 * 不用 `sealed`：[whl.trending.ai.ui.digest.DigestPage] 兼任路由 key 却住在别的包，
 * 而 sealed 的直接实现必须同包。约束力不靠 sealed，靠 backStack 的元素类型——
 * 代价是 `entryProvider` 的 `when` 拿不到穷尽检查，仍需 `else` 兜底。
 */
interface Route {
    /**
     * 本页在埋点里的身份。
     *
     * **容器路由返回 null**：目前只有 [whl.trending.ai.core.Home]，它自己不是页面，
     * 页面身份由内部选中的 tab 决定，由 `HomeScreen` 那一处上报（否则两边各报一条）。
     */
    val screen: Screen?
}
