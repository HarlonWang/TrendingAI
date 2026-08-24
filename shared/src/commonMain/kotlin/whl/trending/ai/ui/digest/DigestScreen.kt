package whl.trending.ai.ui.digest

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.time.Clock
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.action_favorite
import trendingai.shared.generated.resources.back
import trendingai.shared.generated.resources.digest_generated_at
import trendingai.shared.generated.resources.digest_hn_discussion
import trendingai.shared.generated.resources.digest_load_failed
import trendingai.shared.generated.resources.digest_read_original
import trendingai.shared.generated.resources.digest_unavailable_desc
import trendingai.shared.generated.resources.digest_unavailable_title
import trendingai.shared.generated.resources.retry
import whl.trending.ai.core.DigestPage
import whl.trending.ai.core.analytics.AppEvent
import whl.trending.ai.core.analytics.ContentActionKind
import whl.trending.ai.core.analytics.track
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.data.model.FavoriteItem
import whl.trending.ai.data.repository.globalFavoriteRepository
import whl.trending.ai.ui.common.TrendingScaffold
import whl.trending.ai.ui.common.TrendingTopAppBar

private val HnOrange = Color(0xFFFF6600)

/**
 * HN 条目解读页：预生成 AI 解读的统一落点（Feed 列表 / Picks 深读 / 收藏三入口同此）。
 *
 * 设计要点（见父目录 hn-digest-实现方案.md §9 + 已确认 UI 草稿）：
 * - 头部与出路按钮（阅读原文 / HN 讨论区）永远在首屏，任何状态下可用——解读是入口不是替代
 * - 「暂无解读」是明确的占位态，不是空白页；无触发生成的语义，Error 只重试读取
 * - 收藏与列表收藏同一条记录：url 主键 + (source, externalId) 云同步键
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DigestScreen(
    page: DigestPage,
    onBack: () -> Unit,
    onOpenUrl: (url: String) -> Unit,
    viewModel: DigestViewModel = viewModel(key = "${page.source}/${page.externalId}") {
        DigestViewModel(page)
    },
) {
    val uiState by viewModel.uiState.collectAsState()

    val favorites by globalSettingsManager.favorites.collectAsState(emptyList())
    val isFavorite = remember(favorites) { favorites.any { it.url == page.url } }

    TrendingScaffold(
        topBar = {
            TrendingTopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (isFavorite) {
                            globalFavoriteRepository.remove(page.url)
                        } else {
                            globalFavoriteRepository.add(
                                FavoriteItem(
                                    url = page.url,
                                    title = page.title,
                                    source = page.source,
                                    description = page.description,
                                    summary = page.summary,
                                    savedAt = Clock.System.now().toEpochMilliseconds(),
                                    openUrl = page.hnUrl,
                                    externalId = page.externalId
                                )
                            )
                        }
                    }) {
                        Icon(
                            if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = stringResource(Res.string.action_favorite),
                            tint = if (isFavorite) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            DigestHeader(page = page, onOpenUrl = onOpenUrl)
            HorizontalDivider(modifier = Modifier.padding(top = 18.dp, bottom = 4.dp))
            DigestBody(page = page, uiState = uiState, onRetry = viewModel::retry)
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DigestHeader(page: DigestPage, onOpenUrl: (url: String) -> Unit) {
    Text(
        text = page.title,
        fontSize = 19.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 26.sp,
        color = MaterialTheme.colorScheme.onSurface
    )
    Row(
        modifier = Modifier.padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .background(HnOrange, RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = "HN",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        val meta = buildString {
            if (page.score > 0) append("▲ ${page.score}")
            if (page.commentCount > 0) {
                if (isNotEmpty()) append(" · ")
                append("💬 ${page.commentCount}")
            }
            page.author?.takeIf { it.isNotBlank() }?.let {
                if (isNotEmpty()) append(" · ")
                append(it)
            }
        }
        if (meta.isNotEmpty()) {
            Text(
                text = meta,
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    // 出路按钮：首屏常驻、正文之前——解读是入口不是替代
    Row(
        modifier = Modifier.padding(top = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (!page.isSelfPost) {
            FilledTonalButton(onClick = {
                track(AppEvent.ContentAction(ContentActionKind.READ_ORIGINAL, source = page.source, contentId = page.externalId))
                onOpenUrl(page.url)
            }) {
                Icon(
                    Icons.Outlined.Description,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(stringResource(Res.string.digest_read_original))
            }
        }
        FilledTonalButton(onClick = {
            track(AppEvent.ContentAction(ContentActionKind.HN_COMMENTS, source = page.source, contentId = page.externalId))
            onOpenUrl(page.hnUrl)
        }) {
            Icon(
                Icons.Outlined.Forum,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.size(6.dp))
            Text(stringResource(Res.string.digest_hn_discussion))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DigestBody(page: DigestPage, uiState: DigestUiState, onRetry: () -> Unit) {
    when (uiState) {
        is DigestUiState.Loading -> Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
            contentAlignment = Alignment.Center
        ) {
            LoadingIndicator()
        }

        is DigestUiState.Ready -> {
            DigestMarkdown(
                markdown = uiState.markdown,
                modifier = Modifier.padding(top = 4.dp)
            )
            uiState.createdAt?.takeIf { it.length >= 10 }?.let { createdAt ->
                HorizontalDivider(modifier = Modifier.padding(top = 22.dp, bottom = 12.dp))
                Text(
                    text = stringResource(Res.string.digest_generated_at, createdAt.take(10)),
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        is DigestUiState.Unavailable -> PlaceholderCard(
            title = stringResource(Res.string.digest_unavailable_title),
            desc = stringResource(Res.string.digest_unavailable_desc),
        )

        is DigestUiState.Error -> PlaceholderCard(
            title = stringResource(Res.string.digest_load_failed),
            desc = null,
        ) {
            Button(onClick = onRetry) {
                Text(stringResource(Res.string.retry))
            }
        }
    }
}

@Composable
private fun PlaceholderCard(
    title: String,
    desc: String?,
    action: (@Composable () -> Unit)? = null,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = "📄", fontSize = 30.sp)
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            if (desc != null) {
                Text(
                    text = desc,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            action?.invoke()
        }
    }
}
