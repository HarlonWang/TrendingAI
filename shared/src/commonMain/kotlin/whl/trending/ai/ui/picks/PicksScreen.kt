package whl.trending.ai.ui.picks

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import whl.trending.ai.ui.common.LocalContentBottomPadding
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.picks_label_action
import trendingai.shared.generated.resources.picks_label_alternatives
import trendingai.shared.generated.resources.picks_label_terms
import trendingai.shared.generated.resources.close
import trendingai.shared.generated.resources.picks_newsletter_desc
import trendingai.shared.generated.resources.picks_newsletter_title
import trendingai.shared.generated.resources.picks_no_data
import trendingai.shared.generated.resources.picks_section_debut
import trendingai.shared.generated.resources.picks_section_deep_dive
import trendingai.shared.generated.resources.retry
import whl.trending.ai.core.DateTimeUtils
import whl.trending.ai.core.platform.trackItemClick
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.data.repository.globalFavoriteRepository
import whl.trending.ai.data.model.FavoriteItem
import whl.trending.ai.core.platform.shareText
import whl.trending.ai.core.platform.trackEvent
import whl.trending.ai.data.model.PickItem
import whl.trending.ai.ui.common.ItemActionMenu
import whl.trending.ai.ui.digest.DigestPage
import whl.trending.ai.ui.digest.toDigestPage
import whl.trending.ai.ui.common.aiShareText
import androidx.compose.runtime.remember
import kotlin.time.Clock

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PicksScreen(
    onNavigateToDetail: (owner: String, repo: String) -> Unit,
    onOpenUrl: (url: String) -> Unit,
    onNavigateToSubscribe: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PicksViewModel,
    onOpenDigest: (DigestPage) -> Unit = {}
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
                if (picks == null || (picks.debut.isEmpty() && picks.deepDive.isEmpty())) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = stringResource(Res.string.picks_no_data))
                    }
                } else {
                    val favorites by globalSettingsManager.favorites.collectAsState(emptyList())
                    val favoriteUrls = remember(favorites) { favorites.map { it.url }.toSet() }
                    // 初值同步读，避免已订阅/已关闭用户冷启动时横幅闪现一帧
                    val subscribedEmail by globalSettingsManager.subscribedEmail.collectAsState(
                        remember { globalSettingsManager.currentSubscribedEmail() }
                    )
                    val bannerDismissed by globalSettingsManager.picksNewsletterBannerDismissed.collectAsState(
                        remember { globalSettingsManager.currentPicksNewsletterBannerDismissed() }
                    )
                    PicksList(
                        debut = picks.debut,
                        deepDive = picks.deepDive,
                        favoriteUrls = favoriteUrls,
                        showNewsletterBanner = subscribedEmail == null && !bannerDismissed,
                        onSubscribeClick = {
                            trackEvent("picks_newsletter_banner")
                            onNavigateToSubscribe()
                        },
                        onDismissBanner = {
                            trackEvent("picks_newsletter_banner_dismiss")
                            globalSettingsManager.setPicksNewsletterBannerDismissed(true)
                        },
                        onItemClick = { item, section -> handleItemClick(item, section, onNavigateToDetail, onOpenUrl, onOpenDigest) },
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
    onOpenUrl: (url: String) -> Unit,
    onOpenDigest: (DigestPage) -> Unit
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
    // HN 条目进解读页（与 Feed/收藏同一落点），外链从解读页首屏按钮出去
    if (item.source == "hackernews" && item.externalId.isNotBlank()) {
        onOpenDigest(item.toDigestPage())
        return
    }
    onOpenUrl(item.openUrl)
}

@Composable
private fun PicksList(
    debut: List<PickItem>,
    deepDive: List<PickItem>,
    favoriteUrls: Set<String>,
    showNewsletterBanner: Boolean,
    onSubscribeClick: () -> Unit,
    onDismissBanner: () -> Unit,
    onItemClick: (PickItem, String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        // 末条要能从悬浮底栏下面滚出来
        contentPadding = PaddingValues(bottom = LocalContentBottomPadding.current),
    ) {
        // Newsletter 订阅入口（已订阅或手动关闭后隐藏）：把埋在设置深处的订阅提到每日回访的主场景
        if (showNewsletterBanner) {
            item { NewsletterBanner(onClick = onSubscribeClick, onDismiss = onDismissBanner) }
        }

        // Debut — GitHub 上新
        if (debut.isNotEmpty()) {
            item { SectionHeader(title = stringResource(Res.string.picks_section_debut)) }
            items(debut, key = { "debut_${it.rank}" }) { item ->
                DebutCard(
                    item = item,
                    isFavorite = item.url in favoriteUrls,
                    onToggleFavorite = { togglePickFavorite(item, favoriteUrls) },
                    onClick = { onItemClick(item, "debut") }
                )
            }
        }

        // Deep Dive
        if (deepDive.isNotEmpty()) {
            if (debut.isNotEmpty()) {
                item { SectionDivider() }
            }
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

        // 尾部间距
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun NewsletterBanner(onClick: () -> Unit, onDismiss: () -> Unit) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MailOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.picks_newsletter_title),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = stringResource(Res.string.picks_newsletter_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(Res.string.close),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
                val shareContent = aiShareText(item.title, item.summary, item.openUrl)
                ItemActionMenu(
                    isFavorite = isFavorite,
                    onToggle = onToggleFavorite,
                    onShare = {
                        shareText(shareContent)
                        trackEvent(
                            "share_to_ai",
                            mapOf(
                                "source" to item.source,
                                "has_summary" to !item.summary.isNullOrBlank(),
                                "from" to "list"
                            )
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun DebutCard(
    item: PickItem,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SourceTag(source = item.source, label = item.sourceLabel)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatScore(item.source, item.score),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item.analysis?.let { analysis ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = analysis.core,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (analysis.whyImportant.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = analysis.whyImportant,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                analysis.action?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    )
                }
            } ?: item.summary?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(6.dp))
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // 底部三点菜单（与 DeepDiveCard 同交互模式）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                val shareContent = aiShareText(item.title, item.analysis?.core ?: item.summary, item.openUrl)
                ItemActionMenu(
                    isFavorite = isFavorite,
                    onToggle = onToggleFavorite,
                    onShare = {
                        shareText(shareContent)
                        trackEvent(
                            "share_to_ai",
                            mapOf(
                                "source" to item.source,
                                "has_summary" to (item.analysis != null || !item.summary.isNullOrBlank()),
                                "from" to "debut"
                            )
                        )
                    }
                )
            }
        }
    }
}

private fun togglePickFavorite(item: PickItem, favoriteUrls: Set<String>) {
    if (item.url in favoriteUrls) {
        globalFavoriteRepository.remove(item.url)
    } else {
        globalFavoriteRepository.add(
            FavoriteItem(
                url = item.url,
                title = item.title,
                source = item.source,
                description = item.analysis?.core ?: item.description,
                summary = item.analysis?.whyImportant ?: item.summary,
                savedAt = Clock.System.now().toEpochMilliseconds(),
                openUrl = item.openUrl,
                externalId = item.externalId
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
