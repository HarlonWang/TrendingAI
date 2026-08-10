package whl.trending.ai.ui.common

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 首页悬浮底栏挡住的高度（含系统导航栏 inset）。
 *
 * 底栏是浮在内容之上的胶囊，内容要从它下面穿过去才有 edge-to-edge 的观感；代价是列表最后
 * 一条会被压住。各内容页把这个值加进 `contentPadding` 的底部，最后一条就能滚出来。
 *
 * 只有首页四个 tab 的内容需要消费它；其余页面拿到默认值 0，行为不变。
 */
val LocalContentBottomPadding = compositionLocalOf<Dp> { 0.dp }

/**
 * 首页头部（状态栏 + 顶栏 + Home tab 的三源子 tab 行）挡住的高度，对称于
 * [LocalContentBottomPadding] 的顶部版。
 *
 * 沉浸式浏览（#88）的地基：内容层铺满全高、画在头部之下，顶部空间用列表 `contentPadding`
 * 让出——头部将来收起时内容才能原地全展开，而不需要重排。列表态加进 `contentPadding.top`；
 * 骨架/错误/空态等静态容器直接加 `Modifier.padding(top = ...)`；下拉刷新指示器同样要加，
 * 否则会出生在头部背后。
 *
 * 由 HomeScreen 在**页级**（AnimatedContent 内容 lambda 里）provide——转场期间新旧两页
 * 同时在组合树，Home 有子 tab 行、Picks/我的没有，头部高度不同，provide 在外层会拿错值。
 * 其余页面拿到默认值 0，行为不变。
 */
val LocalContentTopPadding = compositionLocalOf<Dp> { 0.dp }
