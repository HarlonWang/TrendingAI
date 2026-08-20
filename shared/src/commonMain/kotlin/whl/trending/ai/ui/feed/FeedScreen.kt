package whl.trending.ai.ui.feed

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import kotlin.time.Clock
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.no_data
import trendingai.shared.generated.resources.retry
import whl.trending.ai.core.analytics.AppEvent
import whl.trending.ai.core.analytics.ContentActionKind
import whl.trending.ai.core.analytics.track
import whl.trending.ai.core.platform.shareText
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.data.model.FavoriteItem
import whl.trending.ai.data.model.FeedItem
import whl.trending.ai.data.repository.globalFavoriteRepository
import whl.trending.ai.ui.common.AiSummaryBox
import whl.trending.ai.ui.common.ItemActionMenu
import whl.trending.ai.ui.common.LocalContentBottomPadding
import whl.trending.ai.ui.common.LocalContentTopPadding
import whl.trending.ai.ui.common.SourceMetaFooter
import whl.trending.ai.ui.common.aiShareText
import whl.trending.ai.ui.common.updateStampText
import whl.trending.ai.ui.digest.DigestPage
import whl.trending.ai.ui.digest.toDigestPage

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

/** 图库轮播的圆点指示器尺寸。 */
private val INDICATOR_DOT_SIZE = 6.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    modifier: Modifier = Modifier,
    viewModel: FeedViewModel,
    onOpenUrl: (url: String) -> Unit,
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
                // 列表铺满全高后指示器的出生点在头部背后，要往下让（静止截图看不出，拉一下才见）
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = LocalContentTopPadding.current),
            )
        },
        modifier = modifier.fillMaxSize()
    ) {
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(top = LocalContentTopPadding.current),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator(modifier = Modifier.size(48.dp))
                }
            }

            uiState.error != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = LocalContentTopPadding.current)
                        .padding(16.dp),
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
                Box(
                    modifier = Modifier.fillMaxSize().padding(top = LocalContentTopPadding.current),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(Res.string.no_data))
                }
            }

            else -> {
                val favorites by globalSettingsManager.favorites.collectAsState(emptyList())
                val favoriteUrls = remember(favorites) { favorites.map { it.url }.toSet() }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    // 首条从悬浮头部下面滚出来，末条从悬浮底栏下面滚出来
                    contentPadding = PaddingValues(
                        top = LocalContentTopPadding.current,
                        bottom = LocalContentBottomPadding.current,
                    ),
                ) {
                    itemsIndexed(
                        uiState.items,
                        key = { _, item -> "${item.source}_${item.externalId}" }
                    ) { index, item ->
                        FeedItemCard(
                            index = index,
                            item = item,
                            isFavorite = item.url in favoriteUrls,
                            onOpenUrl = onOpenUrl,
                            onOpenDigest = onOpenDigest,
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

                    // 抓取时机行：本源最近一次抓取时刻
                    item {
                        updateStampText(uiState.capturedAt)?.let { SourceMetaFooter(text = it) }
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
    onOpenDigest: (DigestPage) -> Unit,
    onToggleFavorite: () -> Unit
) {
    val gallery = item.galleryImageUrls(HERO_REQUEST_PX)
    val clickModifier = Modifier.clickable {
        track(
            AppEvent.ContentOpened(
                source = item.source,
                contentId = item.externalId,
                rank = index + 1,
                title = item.title,
            )
        )
        // HN 条目整卡点击进解读页（预生成、零等待），外链降级为解读页首屏出路按钮
        if (item.source == "hackernews") {
            onOpenDigest(item.toDigestPage())
        } else {
            onOpenUrl(item.openUrl)
        }
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
                gallery = gallery
            )
        }
    }
}

/**
 * 标题以下的公共部分：描述、AI 摘要、元信息 + 操作菜单。
 *
 * [gallery] 是 Product Hunt 的产品图库：有摘要时收进摘要块内部，
 * 没摘要时单独成块——总之跟在描述之后，不再单独占卡片顶部。
 */
@Composable
private fun FeedItemBody(
    item: FeedItem,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    gallery: List<String> = emptyList()
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
            media = gallery.takeIf { it.isNotEmpty() }?.let { urls -> { HeroGallery(urls = urls) } }
        )
    } else if (gallery.isNotEmpty()) {
        HeroGallery(
            urls = gallery,
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
                track(
                    AppEvent.ContentAction(
                        ContentActionKind.SHARE_TO_AI,
                        source = item.source,
                        contentId = item.externalId,
                        from = "list",
                        hasSummary = !item.summary.isNullOrBlank(),
                    )
                )
            }
        )
    }
}

/**
 * 产品图库。一屏一图、左右滑动切换，多图时底部叠圆点指示器。
 *
 * 画幅按首图的实际比例排布，不裁成固定值——图库里既有 16:9 的演示图也有接近方形的
 * 宣传图，统一裁会切掉半张。加载完成前用 16:9 占位，避免高度从 0 弹开；比例只在极端
 * 长图时才夹住，正常横图不受影响。整组共用首图的比例：同一条内各图尺寸实测基本一致，
 * 而逐页改高度会让列表在滑动中上下跳。
 *
 * 后几张只在翻到时才由 [HorizontalPager] 组合、进而触发下载，
 * 静止在首图的用户不会为多图多付流量。
 */
@Composable
private fun HeroGallery(urls: List<String>, modifier: Modifier = Modifier) {
    // 调用点已按 isNotEmpty 把关，这里再兜一次：空列表就什么都不画，而不是抛异常
    val first = urls.firstOrNull() ?: return
    val firstPainter = rememberAsyncImagePainter(first)
    val firstState by firstPainter.state.collectAsState()
    val ratio = (firstState as? AsyncImagePainter.State.Success)
        ?.painter
        ?.intrinsicSize
        ?.takeIf { it.width > 0f && it.height > 0f }
        ?.let { (it.width / it.height).coerceIn(HERO_MIN_RATIO, HERO_MAX_RATIO) }
        ?: HERO_PLACEHOLDER_RATIO

    if (urls.size == 1) {
        Image(
            painter = firstPainter,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(ratio)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        return
    }

    val pagerState = rememberPagerState(pageCount = { urls.size })
    Box(modifier = modifier) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(ratio)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) { page ->
            // 首页复用外层那个 painter：比例已经从它身上读过，再建一个只是重复请求
            if (page == 0) {
                Image(
                    painter = firstPainter,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                AsyncImage(
                    model = urls[page],
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        GalleryIndicator(
            pageCount = urls.size,
            currentPage = pagerState.currentPage,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
        )
    }
}

/**
 * 图库页码指示器。截图底色深浅不定，圆点垫一层半透明黑底才在两种图上都看得清，
 * 因此这里用固定的黑/白而不是主题色。
 */
@Composable
private fun GalleryIndicator(pageCount: Int, currentPage: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { page ->
            Box(
                modifier = Modifier
                    .size(INDICATOR_DOT_SIZE)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (page == currentPage) 1f else 0.45f))
            )
        }
    }
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
