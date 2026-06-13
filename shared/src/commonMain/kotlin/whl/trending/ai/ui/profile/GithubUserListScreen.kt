package whl.trending.ai.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.list_empty_followers
import trendingai.shared.generated.resources.list_empty_following
import trendingai.shared.generated.resources.list_load_failed
import trendingai.shared.generated.resources.profile_followers
import trendingai.shared.generated.resources.profile_following
import trendingai.shared.generated.resources.profile_retry
import whl.trending.ai.data.remote.GithubUserSummary

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GithubUserListScreen(
    mode: GithubUserListMode,
    onBack: () -> Unit,
) {
    val viewModel: GithubUserListViewModel = viewModel(key = "user_list_$mode") {
        GithubUserListViewModel(mode)
    }
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }
    // VM 为 Activity 级缓存，离开即清空，避免再次进入闪出旧数据（与 ProfileScreen 一致）
    DisposableEffect(Unit) { onDispose { viewModel.onLeave() } }
    val uriHandler = LocalUriHandler.current
    val listState = rememberLazyListState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMore()
    }

    val title = when (mode) {
        GithubUserListMode.FOLLOWERS -> stringResource(Res.string.profile_followers)
        GithubUserListMode.FOLLOWING -> stringResource(Res.string.profile_following)
    }
    val emptyText = when (mode) {
        GithubUserListMode.FOLLOWERS -> stringResource(Res.string.list_empty_followers)
        GithubUserListMode.FOLLOWING -> stringResource(Res.string.list_empty_following)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { LoadingIndicator(modifier = Modifier.size(48.dp)) }

            uiState.isError && uiState.items.isEmpty() -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(Res.string.list_load_failed), Modifier.padding(top = 48.dp))
                Button(onClick = { viewModel.load() }) { Text(stringResource(Res.string.profile_retry)) }
            }

            uiState.items.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    emptyText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                items(uiState.items, key = { it.login }) { user ->
                    GithubUserRow(
                        user = user,
                        onClick = { user.htmlUrl?.let { uriHandler.openUri(it) } },
                    )
                    HorizontalDivider(thickness = 0.5.dp)
                }
                if (uiState.isLoadingMore) {
                    item(key = "loading_more") {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            LoadingIndicator(modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GithubUserRow(user: GithubUserSummary, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model = user.avatarUrl,
            contentDescription = null,
            modifier = Modifier.size(40.dp).clip(CircleShape),
        )
        Text(user.login, style = MaterialTheme.typography.bodyLarge)
    }
}
