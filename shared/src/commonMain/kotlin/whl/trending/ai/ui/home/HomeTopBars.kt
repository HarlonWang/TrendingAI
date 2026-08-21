package whl.trending.ai.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FiberNew
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.app_name
import trendingai.shared.generated.resources.batch_am
import trendingai.shared.generated.resources.batch_pm
import trendingai.shared.generated.resources.filter_new_only
import trendingai.shared.generated.resources.history_trending
import trendingai.shared.generated.resources.new_only_hint
import trendingai.shared.generated.resources.period_daily
import trendingai.shared.generated.resources.period_monthly
import trendingai.shared.generated.resources.period_weekly
import trendingai.shared.generated.resources.picks_title
import trendingai.shared.generated.resources.settings
import whl.trending.ai.ui.common.TrendingTopAppBar
import whl.trending.ai.ui.picks.PicksViewModel
import whl.trending.ai.ui.trending.TrendingViewModel

/**
 * GitHub 源的顶栏：标题点开筛选弹窗，副标题实时显示筛选态。
 *
 * 状态自取 [TrendingViewModel]——与内容区的 TrendingScreen 同处 Home entry 的
 * ViewModelStore，`viewModel()` 拿到的是同一实例，无须由 HomeScreen 透传。
 * 弹窗本体在 TrendingScreen，这里只负责置位可见性。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TrendingTopBar(onSettingsClick: () -> Unit) {
    val viewModel: TrendingViewModel = viewModel { TrendingViewModel() }
    val uiState by viewModel.uiState.collectAsState()
    val selectedPeriod = uiState.selectedPeriod
    val selectedLanguage = uiState.selectedLanguage
    val selectedDate = uiState.selectedDate
    val selectedBatch = uiState.selectedBatch

    val periodLabel = when (selectedPeriod) {
        "daily" -> stringResource(Res.string.period_daily)
        "weekly" -> stringResource(Res.string.period_weekly)
        "monthly" -> stringResource(Res.string.period_monthly)
        else -> selectedPeriod
    }

    TrendingTopAppBar(
        title = {
            Column(
                modifier = Modifier
                    .clickable { viewModel.setFilterSheetVisible(true) }
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(Res.string.app_name),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp).padding(start = 4.dp)
                    )
                }

                val langLabel = selectedLanguage.replaceFirstChar { it.uppercase() }
                val subTitle = buildString {
                    append("$periodLabel · $langLabel")
                    if (!selectedDate.isNullOrEmpty()) {
                        val batchLabel = if (selectedBatch == "am") stringResource(Res.string.batch_am) else stringResource(Res.string.batch_pm)
                        append(" · $selectedDate ($batchLabel)")
                    }
                }

                Text(
                    text = subTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        navigationIcon = {
            Box(modifier = Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                Icon(
                    painter = githubLogoPainter(),
                    contentDescription = "GitHub",
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        actions = {
            // 「只看 New」仅 daily 全语言榜有效，其他视图（按语言/周/月）隐藏该按钮
            if (selectedPeriod == "daily" && selectedLanguage == "all") {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                        TooltipAnchorPosition.Above
                    ),
                    tooltip = {
                        RichTooltip(
                            title = { Text(stringResource(Res.string.filter_new_only)) },
                        ) {
                            Text(stringResource(Res.string.new_only_hint))
                        }
                    },
                    state = rememberTooltipState(),
                ) {
                    FilledIconToggleButton(
                        checked = uiState.newOnly,
                        onCheckedChange = { viewModel.toggleNewOnly() },
                        colors = IconButtonDefaults.filledIconToggleButtonColors(
                            containerColor = Color.Transparent,                        // 未选中：无底色，与其他图标一致
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            checkedContainerColor = MaterialTheme.colorScheme.primary,  // 选中：品牌紫实心
                            checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Icon(
                            Icons.Default.FiberNew,
                            contentDescription = stringResource(Res.string.filter_new_only)
                        )
                    }
                }
            }
            IconButton(onClick = { viewModel.setHistorySheetVisible(true) }) {
                Icon(Icons.Default.DateRange, contentDescription = stringResource(Res.string.history_trending))
            }
            SettingsAction(onClick = onSettingsClick)
        }
    )
}

/**
 * 首页各 tab 顶栏右上角统一的设置入口。
 *
 * 底栏「⋯」里那项藏在浮层里，用户不点开就看不见；顶栏给一个常驻齿轮当主入口，
 * 「⋯」保留作为次要路径（关于页仍只在那里）。位置与 Echo 一致：actions 的最末一个。
 */
@Composable
internal fun SettingsAction(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.Default.Settings, contentDescription = stringResource(Res.string.settings))
    }
}

/**
 * Picks 顶栏：副标题带当期日期。日期自取 [PicksViewModel]（同 store 同实例）；
 * 本顶栏只在 Picks tab 选中时组合，VM 的创建时机与改造前「选中才建」一致。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PicksTopBar(onSettingsClick: () -> Unit) {
    val viewModel: PicksViewModel = viewModel { PicksViewModel() }
    val uiState by viewModel.uiState.collectAsState()
    val date = uiState.picks?.metadata?.date
    TrendingTopAppBar(
        title = {
            Column {
                Text(
                    text = stringResource(Res.string.picks_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = buildString {
                        append("GitHub · Hacker News · Product Hunt")
                        if (!date.isNullOrBlank()) append(" · $date")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        actions = { SettingsAction(onClick = onSettingsClick) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeedTopBar(
    title: String,
    navigationIcon: @Composable () -> Unit,
    onSettingsClick: () -> Unit,
    /**
     * 设置按钮**之前**的额外动作，默认空。
     * 只有 HN 分支会传（招聘月度专题入口）——刻意开在 FeedTopBar 这一层而不是
     * TrendingTopAppBar / SettingsAction 那种公共件里，否则 GitHub/PH/Me 页面
     * 会跟着长出一个无意义的按钮。
     */
    leadingActions: @Composable RowScope.() -> Unit = {},
) {
    TrendingTopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
        },
        navigationIcon = {
            Box(modifier = Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                navigationIcon()
            }
        },
        actions = {
            leadingActions()
            SettingsAction(onClick = onSettingsClick)
        },
    )
}
