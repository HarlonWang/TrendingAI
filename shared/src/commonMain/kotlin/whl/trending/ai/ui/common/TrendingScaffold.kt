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
 * 手工传参时只要有一处忘了写，那一页就静默失去滚动变色——正是这次要统一掉的问题。
 */
@OptIn(ExperimentalMaterial3Api::class)
val LocalTopAppBarScrollBehavior = compositionLocalOf<TopAppBarScrollBehavior?> { null }

/**
 * 全 app 统一的页面骨架：在 [Scaffold] 基础上把顶栏的滚动联动接好。
 *
 * 内容滚动时，顶栏底色会从 `surface` 过渡到 `surfaceContainer`（M3 的 scrolledContainerColor），
 * 用一层底色把顶栏和滚上来的内容区分开。行为固定为 [TopAppBarDefaults.pinnedScrollBehavior]：
 * 顶栏只变色、不折叠隐藏。
 *
 * 页面不要直接用 [Scaffold] + [TopAppBar]，否则拿不到这套联动。
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
 * 不在 [TrendingScaffold] 里时（LocalTopAppBarScrollBehavior 为 null）退化成普通 [TopAppBar]。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendingTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
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
