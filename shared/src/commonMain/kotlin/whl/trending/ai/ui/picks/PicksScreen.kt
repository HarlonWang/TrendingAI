package whl.trending.ai.ui.picks

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import whl.trending.ai.core.DateTimeUtils
import whl.trending.ai.core.platform.openUrl
import whl.trending.ai.data.model.PickAnalysis
import whl.trending.ai.data.model.PickItem

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PicksScreen(
    onNavigateToDetail: (owner: String, repo: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PicksViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    when {
        uiState.isLoading -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingIndicator(modifier = Modifier.size(48.dp))
            }
        }

        uiState.error != null -> {
            Column(
                modifier = modifier.fillMaxSize().padding(16.dp),
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
                    Text("重试")
                }
            }
        }

        else -> {
            val picks = uiState.picks
            if (picks == null || (picks.deepDive.isEmpty() && picks.controversy.isEmpty() && picks.speedRead.isEmpty())) {
                Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "暂无精选")
                }
            } else {
                PicksList(
                    deepDive = picks.deepDive,
                    controversy = picks.controversy,
                    speedRead = picks.speedRead,
                    onItemClick = { item -> handleItemClick(item, onNavigateToDetail) },
                    modifier = modifier
                )
            }
        }
    }
}

private fun handleItemClick(
    item: PickItem,
    onNavigateToDetail: (owner: String, repo: String) -> Unit
) {
    if (item.source == "github") {
        val parts = item.url.removePrefix("https://github.com/").split("/")
        if (parts.size >= 2) {
            onNavigateToDetail(parts[0], parts[1])
            return
        }
    }
    openUrl(item.url)
}

@Composable
private fun PicksList(
    deepDive: List<PickItem>,
    controversy: List<PickItem>,
    speedRead: List<PickItem>,
    onItemClick: (PickItem) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // Deep Dive
        if (deepDive.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "深度解读",
                    color = MaterialTheme.colorScheme.primary
                )
            }
            items(deepDive, key = { "deep_${it.rank}" }) { item ->
                DeepDiveCard(item = item, onClick = { onItemClick(item) })
            }
        }

        // Controversy
        if (controversy.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "争议话题",
                    color = Color(0xFFFF6B6B)
                )
            }
            items(controversy, key = { "controversy_${it.rank}" }) { item ->
                ControversyCard(item = item, onClick = { onItemClick(item) })
            }
        }

        // Speed Read
        if (speedRead.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Top 5 速览",
                    color = Color(0xFF03DAC6)
                )
            }
            items(speedRead, key = { "speed_${it.rank}" }) { item ->
                SpeedReadItem(item = item, onClick = { onItemClick(item) })
            }
            // 尾部间距
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SectionHeader(title: String, color: Color) {
    Text(
        text = title,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = color
    )
}

@Composable
private fun DeepDiveCard(item: PickItem, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 标题行 + 源标签
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(8.dp))
            SourceTag(source = item.source, label = item.sourceLabel)
        }

        // Analysis
        item.analysis?.let { analysis ->
            // Core
            Text(
                text = analysis.core,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )

            // Why important
            Text(
                text = analysis.whyImportant,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Community voice
            CommunityVoiceRow(analysis)

            // Action + Alternatives
            ActionAlternativesRow(analysis)
        }
    }
}

@Composable
private fun ControversyCard(item: PickItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // 红色左边框
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(80.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFFFF6B6B))
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp)
                )
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 标题 + 源标签
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
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
                    color = Color(0xFFFF6B6B)
                )
                CommunityVoiceRow(analysis)
            }
        }
    }
}

@Composable
private fun SpeedReadItem(item: PickItem, onClick: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 序号
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(
                        color = Color(0xFF03DAC6),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${item.rank}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
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

            // 分数
            Text(
                text = formatScore(item.source, item.score),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    }
}

@Composable
private fun CommunityVoiceRow(analysis: PickAnalysis) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "\uD83D\uDC4D ${analysis.communityVoice.positive}",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF81C784),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "\uD83D\uDC4E ${analysis.communityVoice.negative}",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFE57373),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ActionAlternativesRow(analysis: PickAnalysis) {
    val action = analysis.action?.takeIf { it.isNotBlank() }
    val alternatives = analysis.alternatives?.takeIf { it.isNotBlank() }
    if (action == null && alternatives == null) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (action != null) {
            TagChip(text = action, color = MaterialTheme.colorScheme.primary)
        }
        if (alternatives != null) {
            TagChip(text = alternatives, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TagChip(text: String, color: Color) {
    Text(
        text = text,
        modifier = Modifier
            .background(
                color = color.copy(alpha = 0.12f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 8.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall,
        color = color
    )
}

@Composable
private fun SourceTag(source: String, label: String) {
    val (bgColor, textColor) = when (source) {
        "github" -> Color(0xFF1B5E20) to Color(0xFFA5D6A7)
        "hackernews" -> Color(0xFFE65100) to Color(0xFFFFCC80)
        "producthunt" -> Color(0xFFDA552F) to Color.White
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = label,
        modifier = Modifier
            .background(color = bgColor, shape = RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        fontSize = 10.sp,
        color = textColor
    )
}

private fun formatScore(source: String, score: Int): String {
    return when (source) {
        "github" -> "★ ${DateTimeUtils.formatNumber(score)}"
        else -> "▲ $score"
    }
}
