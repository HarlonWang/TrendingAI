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
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.MenuBook
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import trendingai.shared.generated.resources.digest_open_github
import trendingai.shared.generated.resources.digest_open_product
import trendingai.shared.generated.resources.digest_ph_page
import trendingai.shared.generated.resources.digest_read_original
import trendingai.shared.generated.resources.digest_unavailable_desc
import trendingai.shared.generated.resources.digest_unavailable_desc_github
import trendingai.shared.generated.resources.digest_unavailable_desc_producthunt
import trendingai.shared.generated.resources.digest_unavailable_title
import trendingai.shared.generated.resources.digest_view_readme
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
private val GithubDark = Color(0xFF24292F)
private val PhRed = Color(0xFFDA552F)

/**
 * 条目解读页：三源预生成 AI 解读的统一落点（列表 / Picks / 收藏各入口同此）。
 *
 * 设计要点（见父目录 archive/hn-digest-实现方案.md §9、github-digest-实现方案.md §7、ph-digest-实现方案.md §6）：
 * - 头部与出路按钮永远在首屏，任何状态下可用——解读是入口不是替代；出路按源不同：
 *   HN 阅读原文 / 讨论区，GitHub 查看 README（App 内）/ 在 GitHub 打开，PH 打开产品 / PH 页面
 * - 「暂无解读」是明确的占位态，不是空白页；无触发生成的语义，Error 只重试读取
 * - 收藏与列表收藏同一条记录：url 主键 + (source, externalId) 云同步键
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DigestScreen(
    page: DigestPage,
    onBack: () -> Unit,
    onOpenUrl: (url: String) -> Unit,
    onOpenRepo: (owner: String, repo: String) -> Unit,
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
                                    openUrl = page.discussionUrl,
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
            DigestHeader(page = page, onOpenUrl = onOpenUrl, onOpenRepo = onOpenRepo)
            HorizontalDivider(modifier = Modifier.padding(top = 18.dp, bottom = 4.dp))
            DigestBody(page = page, uiState = uiState, onRetry = viewModel::retry)
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DigestHeader(
    page: DigestPage,
    onOpenUrl: (url: String) -> Unit,
    onOpenRepo: (owner: String, repo: String) -> Unit,
) {
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
        val (badge, badgeColor) = when {
            page.isGithub -> "GH" to GithubDark
            page.isProductHunt -> "PH" to PhRed
            else -> "HN" to HnOrange
        }
        Box(
            modifier = Modifier
                .background(badgeColor, RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = badge,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        val meta = if (page.isGithub) githubMeta(page) else socialMeta(page)
        if (meta.isNotEmpty()) {
            Text(
                text = meta,
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    // GitHub 的 title 就是 owner/repo，描述另起一行；HN / PH 的描述在列表已看过且正文会覆盖
    if (page.isGithub) {
        page.description?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                fontSize = 13.5.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
    // 出路按钮：首屏常驻、正文之前——解读是入口不是替代
    Row(
        modifier = Modifier.padding(top = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        when {
            page.isGithub -> {
                val ownerRepo = page.githubOwnerRepo
                if (ownerRepo != null) {
                    ExitButton(Icons.Outlined.MenuBook, stringResource(Res.string.digest_view_readme)) {
                        track(AppEvent.ContentAction(ContentActionKind.READ_ORIGINAL, source = page.source, contentId = page.externalId))
                        onOpenRepo(ownerRepo.first, ownerRepo.second)
                    }
                }
                // 与 README 页的同款按钮一致，不埋点
                ExitButton(Icons.Outlined.Code, stringResource(Res.string.digest_open_github)) {
                    onOpenUrl(page.url)
                }
            }
            page.isProductHunt -> {
                ExitButton(Icons.Outlined.Language, stringResource(Res.string.digest_open_product)) {
                    track(AppEvent.ContentAction(ContentActionKind.READ_ORIGINAL, source = page.source, contentId = page.externalId))
                    onOpenUrl(page.url)
                }
                page.discussionUrl?.let { phUrl ->
                    ExitButton(Icons.Outlined.Forum, stringResource(Res.string.digest_ph_page)) {
                        track(AppEvent.ContentAction(ContentActionKind.PH_PAGE, source = page.source, contentId = page.externalId))
                        onOpenUrl(phUrl)
                    }
                }
            }
            else -> {
                if (!page.isSelfPost) {
                    ExitButton(Icons.Outlined.Description, stringResource(Res.string.digest_read_original)) {
                        track(AppEvent.ContentAction(ContentActionKind.READ_ORIGINAL, source = page.source, contentId = page.externalId))
                        onOpenUrl(page.url)
                    }
                }
                page.discussionUrl?.let { hnUrl ->
                    ExitButton(Icons.Outlined.Forum, stringResource(Res.string.digest_hn_discussion)) {
                        track(AppEvent.ContentAction(ContentActionKind.HN_COMMENTS, source = page.source, contentId = page.externalId))
                        onOpenUrl(hnUrl)
                    }
                }
            }
        }
    }
}

/** ● 语言 · ★ stars · +N today；列表带入的当前值，与正文的量级表述不冲突（头部是数据，正文是叙述） */
private fun githubMeta(page: DigestPage): String = buildString {
    page.language?.takeIf { it.isNotBlank() }?.let { append("● $it") }
    if (page.stars > 0) {
        if (isNotEmpty()) append(" · ")
        append("★ ${formatCount(page.stars)}")
    }
    if (page.score > 0) {
        if (isNotEmpty()) append(" · ")
        append("+${formatCount(page.score)}")
    }
}

/** ▲ points/votes · 💬 comments · 作者（HN 仅 Feed 入口有作者） */
private fun socialMeta(page: DigestPage): String = buildString {
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

private fun formatCount(n: Int): String = when {
    n >= 1_000_000 -> "${(n / 100_000) / 10.0}M"
    n >= 1_000 -> "${(n / 100) / 10.0}k"
    else -> n.toString()
}

@Composable
private fun ExitButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.size(6.dp))
        Text(label)
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
            desc = stringResource(
                when {
                    page.isGithub -> Res.string.digest_unavailable_desc_github
                    page.isProductHunt -> Res.string.digest_unavailable_desc_producthunt
                    else -> Res.string.digest_unavailable_desc
                }
            ),
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
