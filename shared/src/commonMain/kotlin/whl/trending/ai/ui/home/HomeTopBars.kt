package whl.trending.ai.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TrendingTopBar(
    selectedPeriod: String,
    selectedLanguage: String,
    selectedDate: String?,
    selectedBatch: String?,
    newOnly: Boolean,
    onToggleNewOnly: () -> Unit,
    onTitleClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
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
                    .clickable { onTitleClick() }
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
                        checked = newOnly,
                        onCheckedChange = { onToggleNewOnly() },
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
            IconButton(onClick = onHistoryClick) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PicksTopBar(date: String?, onSettingsClick: () -> Unit) {
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
        actions = { SettingsAction(onClick = onSettingsClick) },
    )
}
