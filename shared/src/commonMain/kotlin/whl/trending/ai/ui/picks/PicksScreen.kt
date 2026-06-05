package whl.trending.ai.ui.picks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.picks_label_action
import trendingai.shared.generated.resources.picks_label_alternatives
import trendingai.shared.generated.resources.picks_label_terms
import trendingai.shared.generated.resources.picks_no_data
import trendingai.shared.generated.resources.picks_section_controversy
import trendingai.shared.generated.resources.picks_section_deep_dive
import trendingai.shared.generated.resources.picks_section_speed_read
import trendingai.shared.generated.resources.retry
import whl.trending.ai.core.DateTimeUtils
import whl.trending.ai.ui.common.AiSummaryBox
import whl.trending.ai.core.platform.trackItemClick
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.data.model.FavoriteItem
import whl.trending.ai.data.model.PickItem
import whl.trending.ai.ui.common.FavoriteActionMenu
import androidx.compose.runtime.remember
import kotlin.time.Clock

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PicksScreen(
    onNavigateToDetail: (owner: String, repo: String) -> Unit,
    onOpenUrl: (url: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PicksViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val pullToRefreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        state = pullToRefreshState,
        onRefresh = { viewModel.refresh() },
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = pullToRefreshState,
                isRefreshing = uiState.isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
        modifier = modifier.fillMaxSize()
    ) {
        when {
            uiState.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingIndicator(modifier = Modifier.size(48.dp))
                }
            }

            uiState.error != null -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = uiState.error ?: "",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.retry() }) {
                        Text(stringResource(Res.string.retry))
                    }
                }
            }

            else -> {
                val picks = uiState.picks
                if (picks == null || (picks.deepDive.isEmpty() && picks.controversy.isEmpty() && picks.speedRead.isEmpty())) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = stringResource(Res.string.picks_no_data))
                    }
                } else {
                    val favorites by globalSettingsManager.favorites.collectAsState(emptyList())
                    val favoriteUrls = remember(favorites) { favorites.map { it.url }.toSet() }
                    PicksList(
                        deepDive = picks.deepDive,
                        controversy = picks.controversy,
                        speedRead = picks.speedRead,
                        favoriteUrls = favoriteUrls,
                        onItemClick = { item, section -> handleItemClick(item, section, onNavigateToDetail, onOpenUrl) },
                    )
                }
            }
        }
    }
}

private fun handleItemClick(
    item: PickItem,
    section: String,
    onNavigateToDetail: (owner: String, repo: String) -> Unit,
    onOpenUrl: (url: String) -> Unit
) {
    trackItemClick(
        source = item.source,
        rank = item.rank,
        title = item.title,
        section = section
    )
    if (item.source == "github") {
        val parts = item.url.removePrefix("https://github.com/").split("/")
        if (parts.size >= 2) {
            onNavigateToDetail(parts[0], parts[1])
            return
        }
    }
    onOpenUrl(item.url)
}

@Composable
private fun PicksList(
    deepDive: List<PickItem>,
    controversy: List<PickItem>,
    speedRead: List<PickItem>,
    favoriteUrls: Set<String>,
    onItemClick: (PickItem, String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // Deep Dive
        if (deepDive.isNotEmpty()) {
            item { SectionHeader(title = stringResource(Res.string.picks_section_deep_dive)) }
            items(deepDive, key = { "deep_${it.rank}" }) { item ->
                DeepDiveCard(
                    item = item,
                    isFavorite = item.url in favoriteUrls,
                    onToggleFavorite = { togglePickFavorite(item, favoriteUrls) },
                    onClick = { onItemClick(item, "deep_dive") }
                )
            }
        }

        // Controversy
        if (controversy.isNotEmpty()) {
            item { SectionDivider() }
            item { SectionHeader(title = stringResource(Res.string.picks_section_controversy)) }
            item {
                ControversyGroup(
                    items = controversy,
                    favoriteUrls = favoriteUrls,
                    onItemClick = { item -> onItemClick(item, "controversy") }
                )
            }
        }

        // Speed Read
        if (speedRead.isNotEmpty()) {
            item { SectionDivider() }
            item { SectionHeader(title = stringResource(Res.string.picks_section_speed_read)) }
            items(speedRead, key = { "speed_${it.rank}" }) { item ->
                SpeedReadItem(
                    item = item,
                    isFavorite = item.url in favoriteUrls,
                    onToggleFavorite = { togglePickFavorite(item, favoriteUrls) },
                    onClick = { onItemClick(item, "speed_read") }
                )
            }
            // 尾部间距
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        thickness = 2.dp,
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeepDiveCard(item: PickItem, isFavorite: Boolean, onToggleFavorite: () -> Unit, onClick: () -> Unit) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // 头部区域
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(8.dp))
            SourceTag(source = item.source, label = "${item.sourceLabel} ${formatScore(item.source, item.score)}")
        }

        // 正文区域
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            // Analysis
            item.analysis?.let { analysis ->
                // Core — 加粗
                Text(
                    text = analysis.core,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )

                // Why important
                Text(
                    text = analysis.whyImportant,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Action / Alternatives / Terms
                val actionLabel = stringResource(Res.string.picks_label_action)
                val alternativesLabel = stringResource(Res.string.picks_label_alternatives)
                val termsLabel = stringResource(Res.string.picks_label_terms)
                val labels = buildList {
                    analysis.action?.takeIf { it.isNotBlank() }?.let {
                        add(actionLabel to it)
                    }
                    analysis.alternatives?.takeIf { it.isNotBlank() }?.let {
                        add(alternativesLabel to it)
                    }
                    analysis.terms?.takeIf { it.isNotEmpty() }?.let {
                        add(termsLabel to it.joinToString("、"))
                    }
                }
                if (labels.isNotEmpty()) {
                    HorizontalDivider()
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        labels.forEach { (label, value) ->
                            LabeledText(label = label, value = value)
                        }
                    }
                }
            }

            // 底部三点菜单
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                FavoriteActionMenu(
                    isFavorite = isFavorite,
                    onToggle = onToggleFavorite
                )
            }
        }
    }
}

@Composable
private fun ControversyGroup(items: List<PickItem>, favoriteUrls: Set<String>, onItemClick: (PickItem) -> Unit) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        items.forEachIndexed { index, item ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onItemClick(item) }
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    SourceTag(source = item.source, label = item.sourceLabel)
                }

                item.analysis?.let { analysis ->
                    Text(
                        text = analysis.core,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }

                // 底部三点菜单
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    FavoriteActionMenu(
                        isFavorite = item.url in favoriteUrls,
                        onToggle = { togglePickFavorite(item, favoriteUrls) }
                    )
                }
            }
            if (index < items.lastIndex) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

@Composable
private fun SpeedReadItem(item: PickItem, isFavorite: Boolean, onToggleFavorite: () -> Unit, onClick: () -> Unit) {
    Column {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 序号
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${item.rank}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }

                // 标题
                Text(
                    text = item.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // 来源标签 + 分数
                SourceTag(source = item.source, label = "${item.sourceLabel} ${formatScore(item.source, item.score)}")
            }

            // AI 总结
            if (!item.summary.isNullOrBlank()) {
                AiSummaryBox(
                    summary = item.summary,
                    modifier = Modifier.padding(start = 34.dp)
                )
            }

            // 底部三点菜单
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                FavoriteActionMenu(
                    isFavorite = isFavorite,
                    onToggle = onToggleFavorite
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    }
}

private fun togglePickFavorite(item: PickItem, favoriteUrls: Set<String>) {
    if (item.url in favoriteUrls) {
        globalSettingsManager.removeFavorite(item.url)
    } else {
        globalSettingsManager.addFavorite(
            FavoriteItem(
                url = item.url,
                title = item.title,
                source = item.source,
                description = item.analysis?.core ?: item.description,
                summary = item.analysis?.whyImportant ?: item.summary,
                savedAt = Clock.System.now().toEpochMilliseconds()
            )
        )
    }
}


@Composable
private fun LabeledText(label: String, value: String) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                append("$label: ")
            }
            append(value)
        },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
internal fun SourceTag(source: String, label: String) {
    val bgColor = when (source) {
        "github" -> MaterialTheme.colorScheme.onSurface
        "hackernews" -> Color(0xFFFF6600)
        "producthunt" -> Color(0xFFDA552F)
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val textColor = when (source) {
        "github" -> MaterialTheme.colorScheme.surface
        "hackernews", "producthunt" -> Color.White
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = label,
        modifier = Modifier
            .background(color = bgColor, shape = RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall,
        color = textColor
    )
}

internal fun formatScore(source: String, score: Int): String {
    return when (source) {
        "github" -> "★ ${DateTimeUtils.formatNumber(score)}"
        else -> "▲ $score"
    }
}
