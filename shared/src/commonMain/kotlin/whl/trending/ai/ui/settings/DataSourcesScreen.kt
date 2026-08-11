package whl.trending.ai.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.back
import trendingai.shared.generated.resources.data_sources_desc
import trendingai.shared.generated.resources.data_sources_title
import trendingai.shared.generated.resources.ds_github_schedule
import trendingai.shared.generated.resources.ds_github_scope
import trendingai.shared.generated.resources.ds_github_source
import trendingai.shared.generated.resources.ds_hn_schedule
import trendingai.shared.generated.resources.ds_hn_scope
import trendingai.shared.generated.resources.ds_hn_source
import trendingai.shared.generated.resources.ds_label_schedule
import trendingai.shared.generated.resources.ds_label_scope
import trendingai.shared.generated.resources.ds_label_source
import trendingai.shared.generated.resources.ds_ph_schedule
import trendingai.shared.generated.resources.ds_ph_scope
import trendingai.shared.generated.resources.ds_ph_source
import trendingai.shared.generated.resources.ds_picks_schedule
import trendingai.shared.generated.resources.ds_picks_scope
import trendingai.shared.generated.resources.ds_picks_source
import trendingai.shared.generated.resources.github_title
import trendingai.shared.generated.resources.hackernews_title
import trendingai.shared.generated.resources.picks_title
import trendingai.shared.generated.resources.producthunt_title
import whl.trending.ai.ui.common.SettingsGroup
import whl.trending.ai.ui.common.TrendingScaffold
import whl.trending.ai.ui.common.TrendingTopAppBar

/**
 * 数据来源与更新 —— 三源 + Picks 的抓取时机与收录口径，全 app 唯一的口径文案载体。
 *
 * 入口只有「我的 › 关于」一处：列表里的抓取时机行是纯展示（见 [whl.trending.ai.ui.common.SourceMetaFooter]），
 * 挂一个没有图标提示的隐形可点区只会带来误触。刻意也不做源级 InfoDialog——那会让同一份
 * 口径文案在浮层和本页各存一份，两个维护点。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataSourcesScreen(onBack: () -> Unit) {
    TrendingScaffold(
        topBar = {
            TrendingTopAppBar(
                title = { Text(stringResource(Res.string.data_sources_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                Text(
                    text = stringResource(Res.string.data_sources_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                )
            }
            items(SourceSections.size) { index ->
                val section = SourceSections[index]
                SettingsGroup(
                    title = stringResource(section.title),
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    settingsItem(
                        title = { Text(stringResource(Res.string.ds_label_source)) },
                        description = { Text(stringResource(section.source)) },
                    )
                    settingsItem(
                        title = { Text(stringResource(Res.string.ds_label_schedule)) },
                        description = { Text(stringResource(section.schedule)) },
                    )
                    settingsItem(
                        title = { Text(stringResource(Res.string.ds_label_scope)) },
                        description = { Text(stringResource(section.scope)) },
                    )
                }
            }
        }
    }
}

/** 一个源的说明区块。 */
private data class SourceSection(
    val title: StringResource,
    val source: StringResource,
    val schedule: StringResource,
    val scope: StringResource,
)

private val SourceSections = listOf(
    SourceSection(
        title = Res.string.github_title,
        source = Res.string.ds_github_source,
        schedule = Res.string.ds_github_schedule,
        scope = Res.string.ds_github_scope,
    ),
    SourceSection(
        title = Res.string.hackernews_title,
        source = Res.string.ds_hn_source,
        schedule = Res.string.ds_hn_schedule,
        scope = Res.string.ds_hn_scope,
    ),
    SourceSection(
        title = Res.string.producthunt_title,
        source = Res.string.ds_ph_source,
        schedule = Res.string.ds_ph_schedule,
        scope = Res.string.ds_ph_scope,
    ),
    SourceSection(
        title = Res.string.picks_title,
        source = Res.string.ds_picks_source,
        schedule = Res.string.ds_picks_schedule,
        scope = Res.string.ds_picks_scope,
    ),
)
