package whl.trending.ai.ui.favorites

import whl.trending.ai.data.repository.globalFavoriteRepository
import whl.trending.ai.data.model.FavoriteItem
import whl.trending.ai.ui.common.AiSummaryBox
import whl.trending.ai.ui.common.TrendingScaffold
import whl.trending.ai.ui.common.TrendingTopAppBar
import whl.trending.ai.ui.picks.SourceTag
import whl.trending.ai.core.platform.trackEvent
import androidx.compose.runtime.LaunchedEffect

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.back
import trendingai.shared.generated.resources.cancel
import trendingai.shared.generated.resources.confirm
import trendingai.shared.generated.resources.favorites
import trendingai.shared.generated.resources.favorites_delete_confirm
import trendingai.shared.generated.resources.favorites_empty
import trendingai.shared.generated.resources.favorites_empty_hint
import trendingai.shared.generated.resources.favorites_removed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteListScreen(
    onBack: () -> Unit,
    onNavigateToDetail: (owner: String, repo: String) -> Unit = { _, _ -> },
    onOpenUrl: (url: String) -> Unit = {}
) {
    val favorites by globalFavoriteRepository.favorites.collectAsState()

    LaunchedEffect(Unit) {
        trackEvent("favorite_list_view", mapOf("count" to globalFavoriteRepository.currentFavorites().size))
    }

    TrendingScaffold(
        topBar = {
            TrendingTopAppBar(
                title = { Text(stringResource(Res.string.favorites)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back))
                    }
                }
            )
        }
    ) { innerPadding ->
        if (favorites.isEmpty()) {
            Box(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(Res.string.favorites_empty),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(Res.string.favorites_empty_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(innerPadding).fillMaxSize()
            ) {
                itemsIndexed(
                    favorites,
                    key = { _, item -> item.url }
                ) { index, item ->
                    FavoriteCard(
                        item = item,
                        onClick = { handleFavoriteClick(item, onNavigateToDetail, onOpenUrl) },
                        onRemove = { globalFavoriteRepository.toggle(item) }
                    )
                    if (index < favorites.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }
}

private fun handleFavoriteClick(
    item: FavoriteItem,
    onNavigateToDetail: (owner: String, repo: String) -> Unit,
    onOpenUrl: (url: String) -> Unit
) {
    if (item.source == "github") {
        val parts = item.url.removePrefix("https://github.com/").split("/")
        if (parts.size >= 2) {
            onNavigateToDetail(parts[0], parts[1])
            return
        }
    }
    onOpenUrl(item.targetUrl)
}

@Composable
private fun FavoriteCard(item: FavoriteItem, onClick: () -> Unit, onRemove: () -> Unit) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(Res.string.favorites_removed)) },
            text = { Text(stringResource(Res.string.favorites_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onRemove()
                }) {
                    Text(stringResource(Res.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 第一行：来源标签 + 时间 + 删除按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val displayLabel = when (item.source) {
                "github" -> "GitHub"
                "hackernews" -> "Hacker News"
                "producthunt" -> "Product Hunt"
                else -> item.source
            }
            SourceTag(source = item.source, label = displayLabel)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = formatSavedAt(item.savedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 标题
        Text(
            text = item.title,
            fontSize = 16.sp,
            fontWeight = FontWeight.W500,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        // 描述
        if (!item.description.isNullOrBlank()) {
            Text(
                text = item.description,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        // AI 摘要（复用 AiSummaryBox 组件，保持样式一致）
        if (!item.summary.isNullOrBlank()) {
            AiSummaryBox(summary = item.summary)
        }
    }
}

private fun formatSavedAt(timestamp: Long): String {
    val instant = Instant.fromEpochMilliseconds(timestamp)
    val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${local.year}/${local.monthNumber}/${local.dayOfMonth}"
}
