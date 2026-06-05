package whl.trending.ai.ui.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.no_data
import trendingai.shared.generated.resources.retry
import whl.trending.ai.core.platform.trackItemClick
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.data.model.FavoriteItem
import whl.trending.ai.data.model.FeedItem
import whl.trending.ai.ui.common.AiSummaryBox
import whl.trending.ai.ui.common.FavoriteActionMenu
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.fillMaxWidth
import kotlin.time.Clock

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    modifier: Modifier = Modifier,
    viewModel: FeedViewModel,
    onOpenUrl: (url: String) -> Unit
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

            uiState.items.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(Res.string.no_data))
                }
            }

            else -> {
                val favorites by globalSettingsManager.favorites.collectAsState(emptyList())
                val favoriteUrls = remember(favorites) { favorites.map { it.url }.toSet() }
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(
                        uiState.items,
                        key = { _, item -> "${item.source}_${item.externalId}" }
                    ) { index, item ->
                        FeedItemCard(
                            index = index,
                            item = item,
                            isFavorite = item.url in favoriteUrls,
                            onOpenUrl = onOpenUrl,
                            onToggleFavorite = {
                                if (item.url in favoriteUrls) {
                                    globalSettingsManager.removeFavorite(item.url)
                                } else {
                                    globalSettingsManager.addFavorite(
                                        FavoriteItem(
                                            url = item.url,
                                            title = item.title,
                                            source = item.source,
                                            description = item.description,
                                            summary = item.summary,
                                            savedAt = Clock.System.now().toEpochMilliseconds()
                                        )
                                    )
                                }
                            }
                        )
                        if (index < uiState.items.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedItemCard(
    index: Int,
    item: FeedItem,
    isFavorite: Boolean,
    onOpenUrl: (url: String) -> Unit,
    onToggleFavorite: () -> Unit
) {
    Row(
        modifier = Modifier
            .clickable {
                trackItemClick(
                    source = item.source,
                    rank = index + 1,
                    title = item.title
                )
                onOpenUrl(item.url)
            }
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            modifier = Modifier.size(28.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "${index + 1}", fontSize = 12.sp, fontWeight = FontWeight.W500)
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = item.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.W500,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!item.description.isNullOrBlank()) {
                Text(
                    text = item.description,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    maxLines = if (item.source == "producthunt") 2 else Int.MAX_VALUE,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!item.summary.isNullOrBlank()) {
                AiSummaryBox(summary = item.summary)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    FeedItemMetadata(item = item)
                }
                FavoriteActionMenu(
                    isFavorite = isFavorite,
                    onToggle = onToggleFavorite
                )
            }
        }
    }
}

@Composable
private fun FeedItemMetadata(item: FeedItem) {
    val metadataText = buildString {
        append("▲ ${item.score}")
        if (item.commentCount > 0) append(" · 💬 ${item.commentCount}")
        if (!item.author.isNullOrBlank()) append(" · ${item.author}")
    }
    Text(
        text = metadataText,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
