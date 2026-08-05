package whl.trending.ai.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.account_github_entry
import trendingai.shared.generated.resources.feed_created_branch
import trendingai.shared.generated.resources.feed_created_repo
import trendingai.shared.generated.resources.feed_created_tag
import trendingai.shared.generated.resources.feed_empty
import trendingai.shared.generated.resources.feed_end_notice
import trendingai.shared.generated.resources.feed_filter_all
import trendingai.shared.generated.resources.feed_filter_highlights
import trendingai.shared.generated.resources.feed_forked
import trendingai.shared.generated.resources.feed_forked_your_repo
import trendingai.shared.generated.resources.feed_issue_closed
import trendingai.shared.generated.resources.feed_issue_commented
import trendingai.shared.generated.resources.feed_issue_opened
import trendingai.shared.generated.resources.feed_made_public
import trendingai.shared.generated.resources.feed_other
import trendingai.shared.generated.resources.feed_pr_closed
import trendingai.shared.generated.resources.feed_pr_merged
import trendingai.shared.generated.resources.feed_pr_opened
import trendingai.shared.generated.resources.feed_pushed
import trendingai.shared.generated.resources.feed_released
import trendingai.shared.generated.resources.feed_rules_all_desc
import trendingai.shared.generated.resources.feed_rules_all_title
import trendingai.shared.generated.resources.feed_rules_highlights_desc
import trendingai.shared.generated.resources.feed_rules_highlights_title
import trendingai.shared.generated.resources.feed_rules_limits
import trendingai.shared.generated.resources.feed_rules_title
import trendingai.shared.generated.resources.feed_rules_vs_official_desc
import trendingai.shared.generated.resources.feed_rules_vs_official_title
import trendingai.shared.generated.resources.feed_starred
import trendingai.shared.generated.resources.feed_starred_your_repo
import trendingai.shared.generated.resources.feed_unavailable
import trendingai.shared.generated.resources.profile_followers
import trendingai.shared.generated.resources.profile_following
import trendingai.shared.generated.resources.profile_repos
import trendingai.shared.generated.resources.time_days_ago
import trendingai.shared.generated.resources.time_hours_ago
import trendingai.shared.generated.resources.time_just_now
import trendingai.shared.generated.resources.time_minutes_ago
import whl.trending.ai.core.DateTimeUtils
import whl.trending.ai.ui.common.TrendingBottomSheet
import whl.trending.ai.ui.common.TrendingScaffold
import whl.trending.ai.ui.common.TrendingTopAppBar

/**
 * GitHub 开发者档案子页：从账户 Hub 的「GitHub 主页」入口卡进入。
 *
 * 承接原 ProfileScreen 的 GitHub 模块——身份头（头像/名/@login/bio）+ 计数行（可下钻
 * followers/following/repos）+ 贡献热力图 + 精选/全部动态流。与账户 Hub 共享同一
 * [ProfileViewModel]（nav3 Activity 级作用域），进入时数据通常已由 Hub 预加载。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GithubProfileScreen(
    onBack: () -> Unit,
    onOpenFollowers: () -> Unit,
    onOpenFollowing: () -> Unit,
    onOpenRepos: () -> Unit,
) {
    val viewModel: ProfileViewModel = viewModel { ProfileViewModel() }
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }
    val uriHandler = LocalUriHandler.current
    val listState = rememberLazyListState()
    // 贡献热力图横向滚动状态提升到列表外，避免 LazyColumn item 回收时重置到最左
    val contributionScrollState = rememberScrollState()
    val pullToRefreshState = rememberPullToRefreshState()
    var showRulesSheet by remember { mutableStateOf(false) }

    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMoreFeed()
    }

    TrendingScaffold(
        topBar = {
            TrendingTopAppBar(
                title = { Text(stringResource(Res.string.account_github_entry)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        }
    ) { padding ->
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
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "gh_header") {
                    GithubIdentityHeader(
                        uiState = uiState,
                        onOpenFollowers = onOpenFollowers,
                        onOpenFollowing = onOpenFollowing,
                        onOpenRepos = onOpenRepos,
                    )
                }
                uiState.contributions?.let { calendar ->
                    item(key = "contributions") {
                        ContributionGraph(calendar, scrollState = contributionScrollState)
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }
                item(key = "feed_filter") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
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
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { showRulesSheet = true }) {
                            Icon(
                                Icons.Outlined.Info,
                                contentDescription = stringResource(Res.string.feed_rules_title),
                            )
                        }
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
                if (uiState.isFeedLoadingVisible) {
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

    if (showRulesSheet) {
        TrendingBottomSheet(
            onDismissRequest = { showRulesSheet = false },
            title = stringResource(Res.string.feed_rules_title),
        ) {
            FeedRulesSheet()
        }
    }
}

@Composable
private fun GithubIdentityHeader(
    uiState: ProfileUiState,
    onOpenFollowers: () -> Unit,
    onOpenFollowing: () -> Unit,
    onOpenRepos: () -> Unit,
) {
    val user = uiState.user ?: return
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (user.avatarUrl != null) {
            AsyncImage(
                model = user.avatarUrl,
                contentDescription = null,
                modifier = Modifier.size(96.dp).clip(CircleShape)
            )
        }
        val title = user.displayName ?: user.githubLogin.orEmpty()
        if (title.isNotBlank()) {
            Text(title, style = MaterialTheme.typography.titleLarge)
        }
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
                CountCell(gh.followers, stringResource(Res.string.profile_followers), onClick = onOpenFollowers)
                CountCell(gh.following, stringResource(Res.string.profile_following), onClick = onOpenFollowing)
                CountCell(gh.publicRepos, stringResource(Res.string.profile_repos), onClick = onOpenRepos)
            }
        }
    }
}

@Composable
private fun CountCell(count: Int, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(DateTimeUtils.formatNumber(count), style = MaterialTheme.typography.titleMedium)
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FeedRulesSheet() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FeedRulesSection(
            title = stringResource(Res.string.feed_rules_highlights_title),
            body = stringResource(Res.string.feed_rules_highlights_desc),
        )
        FeedRulesSection(
            title = stringResource(Res.string.feed_rules_all_title),
            body = stringResource(Res.string.feed_rules_all_desc),
        )
        FeedRulesSection(
            title = stringResource(Res.string.feed_rules_vs_official_title),
            body = stringResource(Res.string.feed_rules_vs_official_desc),
        )
        Text(
            stringResource(Res.string.feed_rules_limits),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FeedRulesSection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
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
            val annotatedSummary = remember(summary, item.repoName) {
                emphasizeRepoName(summary, item.repoName)
            }
            Text(annotatedSummary, style = MaterialTheme.typography.bodyMedium)
            Text(
                relativeTimeText(item.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 在动态文案中加粗仓库名，便于扫读；未命中（如模板不含 repoName）则原样返回。 */
private fun emphasizeRepoName(summary: String, repoName: String): AnnotatedString {
    val idx = if (repoName.isNotEmpty()) summary.indexOf(repoName) else -1
    if (idx < 0) return AnnotatedString(summary)
    return buildAnnotatedString {
        append(summary.substring(0, idx))
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(repoName) }
        append(summary.substring(idx + repoName.length))
    }
}

/** 相对时间文案；超 7 天或解析失败回退到绝对时间。now 在首次组合时取定，列表项足够用。 */
@Composable
private fun relativeTimeText(createdAt: String): String {
    val rt = remember(createdAt) { DateTimeUtils.relativeTime(createdAt) }
    return when (rt.unit) {
        DateTimeUtils.RelativeUnit.JUST_NOW -> stringResource(Res.string.time_just_now)
        DateTimeUtils.RelativeUnit.MINUTES -> stringResource(Res.string.time_minutes_ago, rt.value)
        DateTimeUtils.RelativeUnit.HOURS -> stringResource(Res.string.time_hours_ago, rt.value)
        DateTimeUtils.RelativeUnit.DAYS -> stringResource(Res.string.time_days_ago, rt.value)
        DateTimeUtils.RelativeUnit.ABSOLUTE -> rt.absolute
    }
}
