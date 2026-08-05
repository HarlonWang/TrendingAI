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
