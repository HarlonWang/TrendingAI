package whl.trending.ai.ui.profile

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.feed_created_branch
import trendingai.shared.generated.resources.feed_created_repo
import trendingai.shared.generated.resources.feed_created_tag
import trendingai.shared.generated.resources.feed_empty
import trendingai.shared.generated.resources.feed_end_notice
import trendingai.shared.generated.resources.feed_filter_all
import trendingai.shared.generated.resources.feed_filter_highlights
import trendingai.shared.generated.resources.feed_forked
import trendingai.shared.generated.resources.feed_issue_closed
import trendingai.shared.generated.resources.feed_issue_commented
import trendingai.shared.generated.resources.feed_issue_opened
import trendingai.shared.generated.resources.feed_forked_your_repo
import trendingai.shared.generated.resources.feed_made_public
import trendingai.shared.generated.resources.feed_other
import trendingai.shared.generated.resources.feed_starred_your_repo
import trendingai.shared.generated.resources.feed_pr_closed
import trendingai.shared.generated.resources.feed_pr_merged
import trendingai.shared.generated.resources.feed_pr_opened
import trendingai.shared.generated.resources.feed_pushed
import trendingai.shared.generated.resources.feed_released
import trendingai.shared.generated.resources.feed_starred
import trendingai.shared.generated.resources.feed_unavailable
import trendingai.shared.generated.resources.profile_followers
import trendingai.shared.generated.resources.profile_following
import trendingai.shared.generated.resources.profile_load_failed
import trendingai.shared.generated.resources.profile_open_github
import trendingai.shared.generated.resources.profile_repos
import trendingai.shared.generated.resources.profile_retry
import trendingai.shared.generated.resources.profile_title
import trendingai.shared.generated.resources.sign_out
import whl.trending.ai.core.DateTimeUtils

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProfileScreen(onBack: () -> Unit) {
    val viewModel: ProfileViewModel = viewModel { ProfileViewModel() }
    val uiState by viewModel.uiState.collectAsState()
    // nav3 默认无 per-entry VM 作用域，VM 是 Activity 级缓存；每次进入本页全量重载，
    // 避免登出换账号串号 / feed 失败态永久残留
    LaunchedEffect(Unit) {
        viewModel.load()
    }
    val uriHandler = LocalUriHandler.current
    val listState = rememberLazyListState()

    // 滚动到底部附近时自动加载下一页
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMoreFeed()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.profile_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { LoadingIndicator(modifier = Modifier.size(48.dp)) }

            uiState.isError -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(Res.string.profile_load_failed), Modifier.padding(top = 48.dp))
                Button(onClick = { viewModel.load() }) { Text(stringResource(Res.string.profile_retry)) }
                OutlinedButton(onClick = { viewModel.signOut(); onBack() }) {
                    Text(stringResource(Res.string.sign_out))
                }
            }

            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                item(key = "header") {
                    ProfileHeader(
                        uiState = uiState,
                        onOpenGithub = { url -> uriHandler.openUri(url) },
                        onSignOut = { viewModel.signOut(); onBack() },
                    )
                }
                item(key = "feed_filter") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = uiState.highlightsOnly,
                            onClick = { if (!uiState.highlightsOnly) viewModel.setFeedFilter(true) },
                            label = { Text(stringResource(Res.string.feed_filter_highlights)) }
                        )
                        FilterChip(
                            selected = !uiState.highlightsOnly,
                            onClick = { if (uiState.highlightsOnly) viewModel.setFeedFilter(false) },
                            label = { Text(stringResource(Res.string.feed_filter_all)) }
                        )
                    }
                }
                if (uiState.feedUnavailable && uiState.feedItems.isEmpty()) {
                    item(key = "feed_unavailable") {
                        FeedNotice(stringResource(Res.string.feed_unavailable))
                    }
                } else if (uiState.feedItems.isEmpty() && uiState.feedEndReached) {
                    item(key = "feed_empty") {
                        FeedNotice(stringResource(Res.string.feed_empty))
                    }
                }
                items(uiState.feedItems, key = { it.id }) { item ->
                    GithubFeedRow(item = item, onClick = { uriHandler.openUri(item.targetUrl) })
                    HorizontalDivider(thickness = 0.5.dp)
                }
                if (uiState.isFeedLoading) {
                    item(key = "feed_loading") {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            LoadingIndicator(modifier = Modifier.size(32.dp))
                        }
                    }
                }
                if (uiState.feedEndReached && uiState.feedItems.isNotEmpty()) {
                    item(key = "feed_end") {
                        FeedNotice(stringResource(Res.string.feed_end_notice))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(
    uiState: ProfileUiState,
    onOpenGithub: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    val user = uiState.user ?: return
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AsyncImage(
            model = user.avatarUrl,
            contentDescription = null,
            modifier = Modifier.size(96.dp).clip(CircleShape)
        )
        Text(user.displayName ?: user.githubLogin.orEmpty(), style = MaterialTheme.typography.titleLarge)
        user.githubLogin?.let {
            Text(
                "@$it",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        user.bio?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
        uiState.githubUser?.let { gh ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                CountCell(gh.followers, stringResource(Res.string.profile_followers))
                CountCell(gh.following, stringResource(Res.string.profile_following))
                CountCell(gh.publicRepos, stringResource(Res.string.profile_repos))
            }
        }
        Spacer(Modifier.height(4.dp))
        user.htmlUrl?.let { url ->
            Button(onClick = { onOpenGithub(url) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.profile_open_github))
            }
        }
        OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.sign_out))
        }
    }
}

@Composable
private fun CountCell(count: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(count.toString(), style = MaterialTheme.typography.titleMedium)
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FeedNotice(text: String) {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GithubFeedRow(item: GithubFeedItem, onClick: () -> Unit) {
    val summary = when (item.kind) {
        GithubFeedKind.STARRED -> stringResource(Res.string.feed_starred, item.repoName)
        GithubFeedKind.FORKED -> stringResource(Res.string.feed_forked, item.repoName)
        GithubFeedKind.CREATED_REPO -> stringResource(Res.string.feed_created_repo, item.repoName)
        GithubFeedKind.CREATED_BRANCH -> stringResource(Res.string.feed_created_branch, item.primary.orEmpty(), item.repoName)
        GithubFeedKind.CREATED_TAG -> stringResource(Res.string.feed_created_tag, item.primary.orEmpty(), item.repoName)
        GithubFeedKind.RELEASED -> stringResource(Res.string.feed_released, item.primary.orEmpty(), item.repoName)
        GithubFeedKind.PUSHED -> stringResource(Res.string.feed_pushed, item.primary.orEmpty(), item.repoName)
        GithubFeedKind.PR_OPENED -> stringResource(Res.string.feed_pr_opened, item.primary.orEmpty(), item.repoName)
        GithubFeedKind.PR_MERGED -> stringResource(Res.string.feed_pr_merged, item.primary.orEmpty(), item.repoName)
        GithubFeedKind.PR_CLOSED -> stringResource(Res.string.feed_pr_closed, item.primary.orEmpty(), item.repoName)
        GithubFeedKind.ISSUE_OPENED -> stringResource(Res.string.feed_issue_opened, item.primary.orEmpty(), item.repoName)
        GithubFeedKind.ISSUE_CLOSED -> stringResource(Res.string.feed_issue_closed, item.primary.orEmpty(), item.repoName)
        GithubFeedKind.ISSUE_COMMENTED -> stringResource(Res.string.feed_issue_commented, item.primary.orEmpty(), item.repoName)
        GithubFeedKind.MADE_PUBLIC -> stringResource(Res.string.feed_made_public, item.repoName)
        GithubFeedKind.STARRED_YOUR_REPO -> stringResource(Res.string.feed_starred_your_repo, item.repoName)
        GithubFeedKind.FORKED_YOUR_REPO -> stringResource(Res.string.feed_forked_your_repo, item.repoName)
        GithubFeedKind.OTHER -> stringResource(Res.string.feed_other, item.primary.orEmpty(), item.repoName)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model = item.actorAvatarUrl,
            contentDescription = null,
            modifier = Modifier.size(36.dp).clip(CircleShape),
        )
        Column(Modifier.weight(1f)) {
            Text(
                item.actorLogin,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(summary, style = MaterialTheme.typography.bodyMedium)
            Text(
                DateTimeUtils.formatToLocalTime(item.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
