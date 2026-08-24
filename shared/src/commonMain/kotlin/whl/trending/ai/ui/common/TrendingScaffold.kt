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
 * 走 CompositionLocal 而非逐层传参：顶栏常被拆成私有 composable，手工传参漏一处即静默漏接。
 * 沉浸式浏览不经这条接线（走独立的 translationY，见 HomeImmersive），保留只为二级页写法统一。
 */
@OptIn(ExperimentalMaterial3Api::class)
val LocalTopAppBarScrollBehavior = compositionLocalOf<TopAppBarScrollBehavior?> { null }

/**
 * 全 app 统一的页面骨架：在 [Scaffold] 基础上把顶栏的滚动联动接好，行为固定 pinned（不折叠）。
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
 * 底色恒定为 `surfaceContainer`、**不随滚动变色**（参照 Echo 的 MainActivity 顶栏配色）——
 * M3 默认的滚动变色会在挂子 tab 的页面把头部劈成两截，勿改回，理由详见 TrendingAI/CLAUDE.md 。
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
