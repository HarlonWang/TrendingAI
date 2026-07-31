package whl.trending.ai.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Shortcut
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.time.Clock
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.back
import trendingai.shared.generated.resources.digest_error_generic
import trendingai.shared.generated.resources.digest_generating
import trendingai.shared.generated.resources.digest_login_gate_desc
import trendingai.shared.generated.resources.digest_login_gate_title
import trendingai.shared.generated.resources.digest_no_content
import trendingai.shared.generated.resources.digest_not_found
import trendingai.shared.generated.resources.digest_open_original
import trendingai.shared.generated.resources.digest_quota_device
import trendingai.shared.generated.resources.digest_quota_global
import trendingai.shared.generated.resources.digest_title
import trendingai.shared.generated.resources.readme_fab_chat
import trendingai.shared.generated.resources.retry
import trendingai.shared.generated.resources.share_to_ai
import trendingai.shared.generated.resources.sign_in
import whl.trending.ai.chat.ChatContext
import whl.trending.ai.chat.globalChatScreen
import whl.trending.ai.core.platform.shareText
import whl.trending.ai.core.platform.trackEvent
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.data.model.FavoriteItem
import whl.trending.ai.data.remote.DigestError
import whl.trending.ai.data.repository.globalFavoriteRepository
import whl.trending.ai.ui.common.AiSummaryBox
import whl.trending.ai.ui.common.MarkdownContent
import whl.trending.ai.ui.common.TrendingScaffold
import whl.trending.ai.ui.common.TrendingTopAppBar
import whl.trending.ai.ui.common.aiShareText

/**
 * HN / PH 条目的 AI 解读页。
 *
 * 取代原先「点条目直接外开浏览器」：正文是服务端生成的解读（流式），
 * 原网页退到顶栏的「查看原文」，仍走全局 [whl.trending.ai.core.platform.openUrl] 出口
 * （按用户设置决定系统浏览器还是应用内 WebView）。
 *
 * GitHub 条目不走这里——它有 README 可读，仍进 [ReadmeScreen]。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DigestScreen(
    source: String,
    externalId: String,
    title: String,
    url: String,
    summary: String? = null,
    onBack: () -> Unit,
    onOpenUrl: (url: String) -> Unit = {},
    onNavigateToChat: (ChatContext) -> Unit = {},
    viewModel: DigestViewModel = viewModel(key = "$source/$externalId") {
        DigestViewModel(source, externalId)
    },
) {
    val uiState by viewModel.uiState.collectAsState()
    val favorites by globalSettingsManager.favorites.collectAsState(emptyList())
    val isFavorite = remember(favorites, url) { favorites.any { it.url == url } }

    LaunchedEffect(source, externalId) {
        trackEvent("digest_view", mapOf("source" to source))
    }

    TrendingScaffold(
        topBar = {
            TrendingTopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.digest_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (isFavorite) {
                            globalFavoriteRepository.remove(url)
                        } else {
                            globalFavoriteRepository.add(
                                FavoriteItem(
                                    url = url,
                                    title = title,
                                    source = source,
                                    summary = summary,
                                    savedAt = Clock.System.now().toEpochMilliseconds(),
                                    openUrl = url,
                                    externalId = externalId,
                                ),
                            )
                        }
                    }) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = null,
                        )
                    }
                    // 分享给 AI 带的是列表那句摘要而不是解读全文：解读动辄上千字，
                    // 贴进别家 AI 的输入框只会被截断
                    val shareContent = aiShareText(title, summary, url)
                    IconButton(onClick = {
                        shareText(shareContent)
                        trackEvent(
                            "share_to_ai",
                            mapOf("source" to source, "has_summary" to !summary.isNullOrBlank(), "from" to "digest"),
                        )
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Shortcut,
                            contentDescription = stringResource(Res.string.share_to_ai),
                        )
                    }
                    IconButton(onClick = {
                        trackEvent("digest_open_original", mapOf("source" to source))
                        onOpenUrl(url)
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = stringResource(Res.string.digest_open_original),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (globalChatScreen != null) {
                ExtendedFloatingActionButton(
                    onClick = {
                        onNavigateToChat(
                            ChatContext(
                                title = title,
                                summary = summary,
                                sourceUrl = url,
                                source = source,
                                externalId = externalId,
                            ),
                        )
                    },
                    icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null) },
                    text = { Text(stringResource(Res.string.readme_fab_chat)) },
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DigestHeader(title = title, summary = summary)
            HorizontalDivider()
            when {
                uiState.markdown.isNotBlank() -> {
                    MarkdownContent(uiState.markdown, modifier = Modifier.fillMaxWidth())
                    // 流式未完时在正文末尾续一个指示器，边生成边读不会以为已经完了
                    if (uiState.isStreaming) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            LoadingIndicator(modifier = Modifier.size(24.dp))
                            Text(
                                text = stringResource(Res.string.digest_generating),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                uiState.isStreaming -> DigestPlaceholder()

                uiState.error != null -> DigestErrorState(
                    error = uiState.error!!,
                    onRetry = viewModel::retry,
                    onSignIn = viewModel::signIn,
                    onOpenOriginal = { onOpenUrl(url) },
                )
            }
        }
    }
}

/** 条目头：标题 + 列表页那句 AI 摘要，解读还没到之前先给读者一点信息 */
@Composable
private fun DigestHeader(title: String, summary: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.W600,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (!summary.isNullOrBlank()) {
            AiSummaryBox(summary = summary)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DigestPlaceholder() {
    Box(
        modifier = Modifier.fillMaxWidth().height(220.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LoadingIndicator(modifier = Modifier.size(48.dp))
            Text(
                text = stringResource(Res.string.digest_generating),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 失败态。只有登录闸给「登录」主按钮（这是唯一一种用户操作即可解决的失败）；
 * 素材不足与条目失效给「查看原文」兜底，其余给重试。
 */
@Composable
private fun DigestErrorState(
    error: DigestError,
    onRetry: () -> Unit,
    onSignIn: () -> Unit,
    onOpenOriginal: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (error) {
                DigestError.LoginRequired -> {
                    Text(
                        text = stringResource(Res.string.digest_login_gate_title),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(Res.string.digest_login_gate_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = onSignIn) { Text(stringResource(Res.string.sign_in)) }
                    OutlinedButton(onClick = onOpenOriginal) {
                        Text(stringResource(Res.string.digest_open_original))
                    }
                }

                is DigestError.Quota -> {
                    Text(
                        text = stringResource(
                            if (error.global) Res.string.digest_quota_global else Res.string.digest_quota_device,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    OutlinedButton(onClick = onOpenOriginal) {
                        Text(stringResource(Res.string.digest_open_original))
                    }
                }

                DigestError.NoContent, DigestError.NotFound -> {
                    Text(
                        text = stringResource(
                            if (error == DigestError.NoContent) {
                                Res.string.digest_no_content
                            } else {
                                Res.string.digest_not_found
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = onOpenOriginal) {
                        Text(stringResource(Res.string.digest_open_original))
                    }
                }

                is DigestError.Retryable -> {
                    Text(
                        text = stringResource(Res.string.digest_error_generic),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = onRetry) { Text(stringResource(Res.string.retry)) }
                    OutlinedButton(onClick = onOpenOriginal) {
                        Text(stringResource(Res.string.digest_open_original))
                    }
                }
            }
        }
    }
}
