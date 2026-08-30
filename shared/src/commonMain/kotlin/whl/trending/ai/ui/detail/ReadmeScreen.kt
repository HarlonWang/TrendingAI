package whl.trending.ai.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Shortcut
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.action_star
import trendingai.shared.generated.resources.action_unstar
import trendingai.shared.generated.resources.back
import trendingai.shared.generated.resources.readme_fab_detail_summary
import trendingai.shared.generated.resources.readme_no_content
import trendingai.shared.generated.resources.retry
import trendingai.shared.generated.resources.share_to_ai
import trendingai.shared.generated.resources.sign_in
import trendingai.shared.generated.resources.star_failed
import trendingai.shared.generated.resources.star_need_login
import trendingai.shared.generated.resources.star_success
import trendingai.shared.generated.resources.unstar_success
import trendingai.shared.generated.resources.view_on_github
import whl.trending.ai.auth.RepoStarService
import whl.trending.chat.ChatContext
import whl.trending.ai.core.analytics.AppEvent
import whl.trending.ai.core.analytics.ContentActionKind
import whl.trending.ai.core.analytics.track
import whl.trending.ai.core.platform.shareText
import whl.trending.ai.ui.common.ErrorState
import whl.trending.ai.ui.common.TrendingScaffold
import whl.trending.ai.ui.common.TrendingTopAppBar
import whl.trending.ai.ui.common.aiShareText

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReadmeScreen(
    owner: String,
    repo: String,
    onBack: () -> Unit,
    onNavigateToChat: (ChatContext) -> Unit = {},
    viewModel: ReadmeViewModel = viewModel(key = "$owner/$repo") {
        ReadmeViewModel(owner, repo)
    }
) {
    val uiState by viewModel.uiState.collectAsState()
    val colorScheme = MaterialTheme.colorScheme
    val webViewColors = WebViewColors(
        bg     = colorScheme.surface.toHex(),
        text   = colorScheme.onSurface.toHex(),
        codeBg = colorScheme.surfaceVariant.toHex(),
        border = colorScheme.outlineVariant.toHex(),
        link   = colorScheme.primary.toHex(),
        muted  = colorScheme.onSurfaceVariant.toHex(),
    )
    val repoUrl = "https://github.com/$owner/$repo"
    val uriHandler = LocalUriHandler.current
    // README 摘录涉及多次正则替换，缓存结果避免每次重组重算（分享栏与 FAB 共用）
    val summary = remember(uiState.html) { readmeExcerpt(uiState.html) }
    // README 正文长度估计（HTML 去标签）；未加载完为 null → 解读 chip / 「一键解读」FAB 不显示
    val readmeLength = remember(uiState.html) {
        uiState.html
            .takeIf { it.isNotBlank() }
            ?.replace(Regex("<[^>]+>"), "")
            ?.length
    }
    // 构造进入 chat 的上下文：本页只有「一键解读」一个 chat 入口，故 autoDetailSummary 恒为 true
    fun buildDetailSummaryContext() = ChatContext(
        title = "$owner/$repo",
        // 带上 README 摘录作为依据，让 AI 能介绍冷门项目；未加载完为 null，退化为仅 title + url
        summary = summary,
        sourceUrl = repoUrl,
        // 「一键详细解读」入参：与服务端 contents 表 (source, external_id) 对齐
        source = "github",
        externalId = "$owner/$repo",
        readmeLength = readmeLength,
        autoDetailSummary = true,
    )
    // 与 chat 模块 DetailSummaryPolicy.MIN_README_CHARS 保持一致（shared 无法跨模块引用该常量）
    val detailSummaryAvailable = (readmeLength ?: 0) >= 1500

    val snackbarHostState = remember { SnackbarHostState() }
    val msgStarred = stringResource(Res.string.star_success)
    val msgUnstarred = stringResource(Res.string.unstar_success)
    val msgFailed = stringResource(Res.string.star_failed)
    val msgNeedLogin = stringResource(Res.string.star_need_login)
    val actionLogin = stringResource(Res.string.sign_in)
    LaunchedEffect(Unit) {
        viewModel.starEvents.collect { result ->
            when (result) {
                RepoStarService.Result.STARRED -> snackbarHostState.showSnackbar(msgStarred)
                RepoStarService.Result.UNSTARRED -> snackbarHostState.showSnackbar(msgUnstarred)
                RepoStarService.Result.FAILED -> snackbarHostState.showSnackbar(msgFailed)
                RepoStarService.Result.NEED_LOGIN -> {
                    val action = snackbarHostState.showSnackbar(
                        message = msgNeedLogin,
                        actionLabel = actionLogin,
                    )
                    if (action == SnackbarResult.ActionPerformed) viewModel.signIn("readme_star_snackbar")
                }
            }
        }
    }

    TrendingScaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TrendingTopAppBar(
                title = {
                    Text(
                        text = "$owner/$repo",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.back)
                        )
                    }
                },
                actions = {
                    if (uiState.starSupported) {
                        IconButton(
                            onClick = { viewModel.toggleStar() },
                            enabled = !uiState.isStarLoading,
                        ) {
                            when {
                                uiState.isStarLoading -> LoadingIndicator(modifier = Modifier.size(24.dp))
                                uiState.isStarred == true -> Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = stringResource(Res.string.action_unstar),
                                )
                                else -> Icon(
                                    imageVector = Icons.Outlined.StarBorder,
                                    contentDescription = stringResource(Res.string.action_star),
                                )
                            }
                        }
                    }
                    val shareContent = aiShareText("$owner/$repo", summary, repoUrl)
                    IconButton(onClick = {
                        shareText(shareContent)
                        track(
                            AppEvent.ContentAction(
                                ContentActionKind.SHARE_TO_AI,
                                source = "github",
                                contentId = "$owner/$repo",
                                from = "detail",
                                hasSummary = !summary.isNullOrBlank(),
                            )
                        )
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Shortcut,
                            contentDescription = stringResource(Res.string.share_to_ai)
                        )
                    }
                    IconButton(onClick = { uriHandler.openUri(repoUrl) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = stringResource(Res.string.view_on_github)
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            // 「一键解读」独占主动作位，一次点击直达（2026-08-12 起）。此前三个 chat 入口
            // 都藏在 FAB 二级菜单里，埋点显示瓶颈在发现而非意愿（进页后仅 18% 展开菜单，
            // 但展开者有 41% 点了解读），故把它提到一级；「AI 对话」与首页 FAB 重复、
            // 「深度调研」在解读卡尾部已有更靠后的热入口，两者一并下掉，别再加回来。
            //
            // 两条约束：
            // - 常驻展开态：WebView 滚动不进 nestedScroll 链，收不到收起信号
            // - README < 1500 字时整颗 FAB 不渲染（与 chat 内 chip 同规则），此时本页无 AI 入口
            if (detailSummaryAvailable) {
                ExtendedFloatingActionButton(
                    // Chat 是路由，screen_viewed(from=readme) 由路由源自动产生，这里不埋点
                    onClick = { onNavigateToChat(buildDetailSummaryContext()) },
                    icon = {
                        // 摊开的书：与首页 chat FAB 的 AutoAwesome(✨) 区分开——✨ 是开放式对话，
                        // 这里通往的是一篇可读完的解读。也不与 DigestScreen 的 Description（读原文）撞形。
                        Icon(Icons.Outlined.AutoStories, contentDescription = null)
                    },
                    text = { Text(stringResource(Res.string.readme_fab_detail_summary)) },
                )
            }
        }
    ) { innerPadding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator(modifier = Modifier.size(48.dp))
                }
            }

            uiState.error != null -> {
                ErrorState(
                    message = uiState.error ?: "",
                    onRetry = { viewModel.fetchReadme() },
                    modifier = Modifier.padding(innerPadding),
                )
            }

            uiState.html.isBlank() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(Res.string.readme_no_content),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            else -> {
                HtmlWebView(
                    html = uiState.html,
                    colors = webViewColors,
                    modifier = Modifier.fillMaxSize().padding(innerPadding)
                        .background(colorScheme.surface)
                )
            }
        }
    }
}

private fun Color.toHex(): String {
    val r = (red * 255 + 0.5f).toInt().coerceIn(0, 255)
    val g = (green * 255 + 0.5f).toInt().coerceIn(0, 255)
    val b = (blue * 255 + 0.5f).toInt().coerceIn(0, 255)
    return "#${r.toString(16).padStart(2, '0')}${g.toString(16).padStart(2, '0')}${b.toString(16).padStart(2, '0')}"
}

/**
 * 从 README 的 HTML 中提取纯文本摘录，作为进入 chat 的 [ChatContext.summary] 依据，
 * 让 AI 也能介绍冷门项目。去标签 + 解码常见实体 + 压空白，截断到 [maxChars]；空则返回 null。
 */
private fun readmeExcerpt(html: String, maxChars: Int = 900): String? {
    if (html.isBlank()) return null
    val text = html
        .replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace(Regex("\\s+"), " ")
        .trim()
    return text.take(maxChars).ifBlank { null }
}
