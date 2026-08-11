package whl.trending.ai.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import trendingai.shared.generated.resources.ds_github_scope
import trendingai.shared.generated.resources.ds_github_source
import trendingai.shared.generated.resources.ds_hn_scope
import trendingai.shared.generated.resources.ds_hn_source
import trendingai.shared.generated.resources.ds_ph_scope
import trendingai.shared.generated.resources.ds_ph_source
import trendingai.shared.generated.resources.ds_picks_source
import trendingai.shared.generated.resources.github_title
import trendingai.shared.generated.resources.hackernews_title
import trendingai.shared.generated.resources.picks_title
import trendingai.shared.generated.resources.producthunt_title
import whl.trending.ai.ui.common.TrendingScaffold
import whl.trending.ai.ui.common.TrendingTopAppBar

/**
 * 数据来源与更新 —— 三源 + Picks 的更新节奏与收录范围，全 app 唯一的口径文案载体。
 *
 * 入口只有「设置 › 通用 › 数据来源与更新」一处：各列表尾部的时机行是纯展示
 * （见 [whl.trending.ai.ui.common.SourceMetaFooter]），挂一个没有图标提示的隐形可点区
 * 只会带来误触。刻意也不做源级 InfoDialog——那会让同一份口径文案在浮层和本页各存一份。
 *
 * **不用 `SettingsGroup`**：那是为「可点击设置项 + trailing 控件」设计的，本页一行都不可点，
 * 用它会长出假的可点感；且它的 title/description 结构逼着每条配一个标签，「来源」「收录范围」
 * 这类零信息量的标签会以 titleMedium 粗体压过灰色正文——最醒目的东西最没用。改成每源一张卡、
 * 源名进卡内当标题，卡片数从 8 张减到 4 张，一屏看完四组。
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
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = stringResource(Res.string.data_sources_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
            items(SourceSections) { section -> SourceCard(section) }
        }
    }
}

@Composable
private fun SourceCard(section: SourceSection) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(section.title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(section.source),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Picks 没有第二行：它的「收录范围」只能落到评分/降权那套内部规则上，不适合透出
            section.scope?.let {
                Text(
                    text = stringResource(it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 一个源的说明卡：源名标题 + 来源（含更新节奏）+ 收录范围（[scope] 为空则只有前两行）。 */
private data class SourceSection(
    val title: StringResource,
    val source: StringResource,
    val scope: StringResource? = null,
)

/**
 * 节奏并进 [SourceSection.source] 而不单列：模糊到「每天更新一次」之后，四组里三组的
 * 节奏文案一模一样，单独占一行是空的。**精确时刻刻意不写**——它由各列表尾部的时机行
 * 按本地时区动态给出（「今天 08:17 更新」），静态文案只答「多久一次」；把 cron 的 UTC
 * 时刻写死在这里，既要用户自己换算时区，调度一改还会过期。
 */
private val SourceSections = listOf(
    SourceSection(
        title = Res.string.github_title,
        source = Res.string.ds_github_source,
        scope = Res.string.ds_github_scope,
    ),
    SourceSection(
        title = Res.string.hackernews_title,
        source = Res.string.ds_hn_source,
        scope = Res.string.ds_hn_scope,
    ),
    SourceSection(
        title = Res.string.producthunt_title,
        source = Res.string.ds_ph_source,
        scope = Res.string.ds_ph_scope,
    ),
    SourceSection(
        title = Res.string.picks_title,
        source = Res.string.ds_picks_source,
    ),
)
