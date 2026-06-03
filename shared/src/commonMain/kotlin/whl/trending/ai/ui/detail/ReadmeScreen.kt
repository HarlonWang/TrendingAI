package whl.trending.ai.ui.detail

import whl.trending.ai.chat.ChatContext
import whl.trending.ai.chat.globalChatScreen
import whl.trending.ai.core.Constants
import whl.trending.ai.core.platform.openUrl

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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.back
import trendingai.shared.generated.resources.readme_no_content
import trendingai.shared.generated.resources.retry
import trendingai.shared.generated.resources.view_on_github

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

    Scaffold(
        topBar = {
            TopAppBar(
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
                    IconButton(onClick = { openUrl(repoUrl, Constants.GITHUB_APP_PACKAGE) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = stringResource(Res.string.view_on_github)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        floatingActionButton = {
            if (globalChatScreen != null) {
                FloatingActionButton(
                    onClick = {
                        onNavigateToChat(
                            ChatContext(
                                title = "$owner/$repo",
                                // 带上 README 摘录作为依据，让 AI 能介绍冷门项目；
                                // README 未加载完则为 null，退化为仅 title + url
                                summary = readmeExcerpt(uiState.html),
                                sourceUrl = repoUrl
                            )
                        )
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "AI"
                    )
                }
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
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = uiState.error ?: "",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(onClick = { viewModel.fetchReadme() }) {
                            Text(stringResource(Res.string.retry))
                        }
                    }
                }
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
