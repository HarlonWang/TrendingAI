package whl.trending.ai.ui.detail

import whl.trending.ai.core.Constants
import whl.trending.ai.core.platform.openUrl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.painter.Painter
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.model.ImageData
import com.mikepenz.markdown.model.ImageTransformer
import org.jetbrains.compose.resources.stringResource
import trending.shared.generated.resources.Res
import trending.shared.generated.resources.back
import trending.shared.generated.resources.readme_no_content
import trending.shared.generated.resources.retry
import trending.shared.generated.resources.view_on_github

/**
 * 将 README 中的相对图片路径补全为 raw.githubusercontent.com 的绝对 URL。
 * 绝对 URL（http/https）直接透传，协议相对 URL 补全 https 前缀。
 */
private fun resolveImageUrl(link: String, owner: String, repo: String, branch: String): String {
    return when {
        link.startsWith("https://") || link.startsWith("http://") -> link
        link.startsWith("//") -> "https:$link"
        else -> {
            val path = link.trimStart('.', '/')
            "https://raw.githubusercontent.com/$owner/$repo/$branch/$path"
        }
    }
}

private fun readmeImageTransformer(owner: String, repo: String, branch: String): ImageTransformer =
    object : ImageTransformer {
        @Composable
        override fun transform(link: String): ImageData {
            val resolved = resolveImageUrl(link, owner, repo, branch)
            return Coil3ImageTransformerImpl.transform(resolved)
        }

        @Composable
        override fun intrinsicSize(painter: Painter): Size =
            Coil3ImageTransformerImpl.intrinsicSize(painter)
    }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReadmeScreen(
    owner: String,
    repo: String,
    onBack: () -> Unit,
    viewModel: ReadmeViewModel = viewModel(key = "$owner/$repo") {
        ReadmeViewModel(owner, repo)
    }
) {
    val uiState by viewModel.uiState.collectAsState()
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
                }
            )
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
                    androidx.compose.foundation.layout.Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
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

            uiState.content.isBlank() -> {
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
                val imageTransformer = remember(owner, repo, uiState.branch) {
                    readmeImageTransformer(owner, repo, uiState.branch)
                }
                Markdown(
                    content = uiState.content,
                    imageTransformer = imageTransformer,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
    }
}
