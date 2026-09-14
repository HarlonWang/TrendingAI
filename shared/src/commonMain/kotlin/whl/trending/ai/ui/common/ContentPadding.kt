package whl.trending.ai.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp

/**
 * 首页悬浮底栏挡住的高度（含系统导航栏 inset）。底栏浮在内容之上，各内容页把这个值
 * 加进 `contentPadding` 的底部，列表最后一条才能滚出来。
 * 只有首页四个 tab 的内容需要消费它；其余页面拿到默认值 0。
 */
val LocalContentBottomPadding = compositionLocalOf { 0.dp }

/**
 * 首页头部（状态栏 + 顶栏 + Home tab 的三源子 tab 行）挡住的高度，对称于
 * [LocalContentBottomPadding] 的顶部版：内容层铺满全高、画在头部之下，顶部空间用
 * `contentPadding` 让出。列表态加进 `contentPadding.top`；骨架/错误/空态等静态容器
 * 直接加 `Modifier.padding(top = ...)`；下拉刷新指示器同样要加，否则会出生在头部背后。
 *
 * 由 [HeaderOverlayLayout] 按头部实测高度 provide。首页嵌两层：外层量顶栏，Home tab 内
 * 再套一层量「顶栏 + 子 tab 行」，Picks/我的 拿外层值、Home 内容拿内层值——转场期间
 * 新旧两页同时在组合树，各自的值随各自的层级走，不会拿错。其余页面拿到默认值 0。
 */
val LocalContentTopPadding = compositionLocalOf { 0.dp }

/**
 * 头部浮在铺满全高的内容之上，内容按头部的**实测**高度让出顶部空间
 * （经 [LocalContentTopPadding] 送达）。
 *
 * 用 [SubcomposeLayout] 先量头部再组合内容，与 M3 Scaffold 给 contentPadding 的方式相同：
 * 实测值在同一帧内到位，不经 onSizeChanged 回写，没有首帧跳动。头部高度不能用规范定值——
 * M3 TopAppBar 是 `max(64dp, 标题高)`、Tab 是 `max(48dp, 文字高 + 基线距)`，大字号下标签折行
 * 就会超出定值，内容首条被压在头部底下。
 *
 * 头部画在内容之后（z-order 在上）。M3 TopAppBar/TabRow 外层的 Surface 自带空 pointerInput，
 * 会吞掉落在其区域内的触摸；若把头部换成非 Surface 容器，必须自行补上这层消费，否则点击穿透。
 */
@Composable
fun HeaderOverlayLayout(
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    SubcomposeLayout(modifier) { constraints ->
        val headerPlaceables = subcompose(HeaderOverlaySlot.Header, header)
            .map { it.measure(constraints.copy(minHeight = 0)) }
        val headerHeight = headerPlaceables.maxOfOrNull { it.height } ?: 0
        val contentPlaceables = subcompose(HeaderOverlaySlot.Content) {
            CompositionLocalProvider(
                LocalContentTopPadding provides headerHeight.toDp(),
                content = content,
            )
        }.map { it.measure(constraints) }

        val width = (headerPlaceables + contentPlaceables).maxOfOrNull { it.width } ?: 0
        val height = contentPlaceables.maxOfOrNull { it.height } ?: headerHeight
        layout(constraints.constrainWidth(width), constraints.constrainHeight(height)) {
            contentPlaceables.forEach { it.place(0, 0) }
            headerPlaceables.forEach { it.place(0, 0) }
        }
    }
}

private enum class HeaderOverlaySlot { Header, Content }
