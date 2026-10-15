package whl.trending.ai.ui.common

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 全 app 统一的顶栏颜色：底色恒定为 `surfaceContainer`、**不随滚动变色**。
 * M3 默认的滚动变色会在挂子 tab 的页面把头部劈成两截，勿改回，理由详见 TrendingAI/CLAUDE.md。
 * appsettings-kmp 自带的页面经 `rememberAppSettings { topBar { containerColor } }` 配成同一个值。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun trendingTopAppBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
)

/**
 * 全 app 统一的顶栏：M3 `TopAppBar` + [trendingTopAppBarColors]，不接滚动行为（颜色恒定、不折叠，接了也没有可见效果）。
 * 页面骨架直接用 M3 `Scaffold`，不再有包装。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendingTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    colors: TopAppBarColors = trendingTopAppBarColors(),
) {
    TopAppBar(
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        colors = colors,
    )
}
