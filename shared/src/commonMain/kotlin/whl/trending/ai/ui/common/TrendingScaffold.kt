package whl.trending.ai.ui.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll

/**
 * 当前页面的顶栏滚动行为，由 [TrendingScaffold] 提供、[TrendingTopAppBar] 自动消费。
 *
 * 走 CompositionLocal 而不是逐层传参，是因为顶栏常被拆成独立的私有 composable（首页四个 tab 各一个），
 * 手工传参时只要有一处忘了写，那一页就静默漏接——顶栏底色现已恒定（见 [TrendingTopAppBar]），
 * 这套接线只剩「让顶栏正常参与 nestedScroll」这一层作用。
 *
 * 注意：将来的沉浸式浏览（#88）**不走** M3 的折叠机制——那套改的是顶栏测量高度，会引起内容
 * 重排；沉浸式走独立的共享偏移 + translationY，且首页顶栏已移出 Scaffold 的 topBar 槽位、
 * 不再经过这条接线。保留它只为二级页写法统一。
 */
@OptIn(ExperimentalMaterial3Api::class)
val LocalTopAppBarScrollBehavior = compositionLocalOf<TopAppBarScrollBehavior?> { null }

/**
 * 全 app 统一的页面骨架：在 [Scaffold] 基础上把顶栏的滚动联动接好。
 *
 * 顶栏底色恒定为 `surfaceContainer`，比内容区（`background`）深一档，靠这层固定的层次差
 * 把顶栏和内容分开——不随滚动变色。行为固定为 [TopAppBarDefaults.pinnedScrollBehavior]：
 * 顶栏不折叠隐藏。
 *
 * 页面不要直接用 [Scaffold] + [TopAppBar]，否则拿不到统一的顶栏配色。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendingScaffold(
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(),
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    containerColor: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = contentColorFor(containerColor),
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    content: @Composable (PaddingValues) -> Unit,
) {
    CompositionLocalProvider(LocalTopAppBarScrollBehavior provides scrollBehavior) {
        Scaffold(
            modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = topBar,
            bottomBar = bottomBar,
            snackbarHost = snackbarHost,
            floatingActionButton = floatingActionButton,
            floatingActionButtonPosition = floatingActionButtonPosition,
            containerColor = containerColor,
            contentColor = contentColor,
            contentWindowInsets = contentWindowInsets,
            content = content,
        )
    }
}

/**
 * 全 app 统一的顶栏：默认取用 [TrendingScaffold] 提供的滚动行为，调用方无需接线。
 *
 * 底色恒定为 `surfaceContainer`（`containerColor` 与 `scrolledContainerColor` 同值），不随滚动变化。
 * 不用 M3 默认的「滚起来才从 surface 变成 surfaceContainer」，是因为那套变色在顶栏下面挂了子 tab 的
 * 页面会把头部劈成两截：首页的三源 `SecondaryTabRow` 是内容区的第一项、底色跟着 `background`，
 * 顶栏一变色就出现一条突兀的横向断层，而 tab 行本身的 divider 已经承担了分隔职责。
 * 恒定底色同时消掉了 pinned 行为下那个 0→1 的阶跃跳变。参照 Echo 的 MainActivity 顶栏配色。
 *
 * 不在 [TrendingScaffold] 里时（LocalTopAppBarScrollBehavior 为 null）退化成普通 [TopAppBar]。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendingTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    ),
    scrollBehavior: TopAppBarScrollBehavior? = LocalTopAppBarScrollBehavior.current,
) {
    TopAppBar(
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        colors = colors,
        scrollBehavior = scrollBehavior,
    )
}
