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
import whl.trending.ai.data.repository.globalFavoriteRepository
import whl.trending.ai.data.model.FavoriteItem
import whl.trending.ai.data.model.FeedItem
import whl.trending.ai.core.platform.shareText
import whl.trending.ai.core.platform.trackEvent
import whl.trending.ai.ui.common.AiSummaryBox
import whl.trending.ai.ui.common.ItemActionMenu
import whl.trending.ai.ui.common.aiShareText
import androidx.compose.runtime.remember
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import kotlin.time.Clock

/** 左侧标识位尺寸，序号圆圈和产品 logo 共用；请求像素按 3x 屏取整，避免高密度屏上发虚。 */
private val LEADING_SIZE = 28.dp
private const val LEADING_REQUEST_PX = 84

/**
 * 主视觉请求宽度。列表里图块实际宽度不到 1000px，这里只要 720：
 * 实测 20 条 720 宽合计约 1.1MB，再往上翻倍的流量换不来肉眼可见的清晰度。
 */
private const val HERO_REQUEST_PX = 720

/** 主视觉比例：加载完成前的占位值，以及夹住极端长图/宽图用的上下界。 */
private const val HERO_PLACEHOLDER_RATIO = 16f / 9f
private const val HERO_MIN_RATIO = 0.75f
private const val HERO_MAX_RATIO = 3f

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
                                    globalFavoriteRepository.remove(item.url)
                                } else {
                                    globalFavoriteRepository.add(
                                        FavoriteItem(
                                            url = item.url,
                                            title = item.title,
                                            source = item.source,
                                            description = item.description,
                                            summary = item.summary,
                                            savedAt = Clock.System.now().toEpochMilliseconds(),
                                            openUrl = item.openUrl,
                                            externalId = item.externalId
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
    val heroImage = item.heroImageUrl(HERO_REQUEST_PX)
    val clickModifier = Modifier.clickable {
        trackItemClick(
            source = item.source,
            rank = index + 1,
            title = item.title
        )
        onOpenUrl(item.openUrl)
    }

    Row(
        modifier = clickModifier.padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        FeedItemLeading(index = index, item = item)
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = item.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.W500,
                color = MaterialTheme.colorScheme.onSurface
            )
            FeedItemBody(
                item = item,
                isFavorite = isFavorite,
                onToggleFavorite = onToggleFavorite,
                heroImage = heroImage
            )
        }
    }
}

/**
 * 标题以下的公共部分：描述、AI 摘要、元信息 + 操作菜单。
 *
 * [heroImage] 是 Product Hunt 的产品主视觉：有摘要时收进摘要块内部，
 * 没摘要时单独成块——总之跟在描述之后，不再单独占卡片顶部。
 */
@Composable
private fun FeedItemBody(
    item: FeedItem,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    heroImage: String? = null
) {
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
        AiSummaryBox(
            summary = item.summary,
            media = heroImage?.let { url -> { HeroImage(url = url) } }
        )
    } else if (heroImage != null) {
        HeroImage(
            url = heroImage,
            modifier = Modifier.clip(RoundedCornerShape(12.dp))
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f)) {
            FeedItemMetadata(item = item)
        }
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

/**
 * 产品主视觉。按图片实际比例排布，不裁成固定画幅——图库里既有 16:9 的演示图
 * 也有接近方形的宣传图，统一裁会切掉半张。加载完成前用 16:9 占位，避免高度从
 * 0 弹开；比例只在极端长图时才夹住，正常横图不受影响。
 */
@Composable
private fun HeroImage(url: String, modifier: Modifier = Modifier) {
    val painter = rememberAsyncImagePainter(url)
    val state by painter.state.collectAsState()
    val ratio = (state as? AsyncImagePainter.State.Success)
        ?.painter
        ?.intrinsicSize
        ?.takeIf { it.width > 0f && it.height > 0f }
        ?.let { (it.width / it.height).coerceIn(HERO_MIN_RATIO, HERO_MAX_RATIO) }
        ?: HERO_PLACEHOLDER_RATIO

    Image(
        painter = painter,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    )
}

/**
 * 列表项左侧的标识位：Product Hunt 放产品 logo，其余来源放排名序号。
 * 两者同尺寸同形状同位置，正下方不排内容——三个 tab 共用一套骨架。
 */
@Composable
private fun FeedItemLeading(index: Int, item: FeedItem) {
    val logo = item.thumbnailUrl(LEADING_REQUEST_PX)
    if (logo != null) {
        AsyncImage(
            model = logo,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(LEADING_SIZE)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
    } else {
        Surface(
            modifier = Modifier.size(LEADING_SIZE),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "${index + 1}", fontSize = 12.sp, fontWeight = FontWeight.W500)
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
