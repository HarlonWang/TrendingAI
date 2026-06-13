package whl.trending.ai.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import trendingai.shared.generated.resources.profile_followers
import trendingai.shared.generated.resources.profile_following
import whl.trending.ai.data.remote.GithubUserSummary

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

    val title = when (mode) {
        GithubUserListMode.FOLLOWERS -> stringResource(Res.string.profile_followers)
        GithubUserListMode.FOLLOWING -> stringResource(Res.string.profile_following)
    }
    val emptyText = when (mode) {
        GithubUserListMode.FOLLOWERS -> stringResource(Res.string.list_empty_followers)
        GithubUserListMode.FOLLOWING -> stringResource(Res.string.list_empty_following)
    }

    PaginatedListScaffold(
        title = title,
        emptyText = emptyText,
        uiState = uiState,
        onBack = onBack,
        onRetry = { viewModel.load() },
        onLoadMore = { viewModel.loadMore() },
        itemKey = { it.login },
    ) { user ->
        GithubUserRow(
            user = user,
            onClick = { user.htmlUrl?.let { uriHandler.openUri(it) } },
        )
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
