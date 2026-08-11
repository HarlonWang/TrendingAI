package whl.trending.ai.ui.trending

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import whl.trending.ai.ui.common.InfoDialog
import whl.trending.ai.ui.common.SourceMetaFooter
import whl.trending.ai.ui.common.snapshotStampText
import whl.trending.ai.ui.common.updateStampText
import whl.trending.ai.ui.common.withBatchSuffix
import whl.trending.ai.ui.common.TrendingBottomSheet
import whl.trending.ai.ui.common.LocalContentBottomPadding
import whl.trending.ai.ui.common.LocalContentTopPadding
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.action_help
import trendingai.shared.generated.resources.batch_am
import trendingai.shared.generated.resources.batch_pm
import trendingai.shared.generated.resources.cancel
import trendingai.shared.generated.resources.click_to_select_date
import trendingai.shared.generated.resources.confirm
import trendingai.shared.generated.resources.error_fetch
import trendingai.shared.generated.resources.filter_done
import trendingai.shared.generated.resources.filter_language
import trendingai.shared.generated.resources.filter_options
import trendingai.shared.generated.resources.filter_period
import trendingai.shared.generated.resources.filter_reset
import trendingai.shared.generated.resources.history_batch
import trendingai.shared.generated.resources.history_date
import trendingai.shared.generated.resources.history_info_content
import trendingai.shared.generated.resources.history_info_title
import trendingai.shared.generated.resources.history_trending
import trendingai.shared.generated.resources.no_data
import trendingai.shared.generated.resources.no_new_repos
import trendingai.shared.generated.resources.period_daily
import trendingai.shared.generated.resources.period_monthly
import trendingai.shared.generated.resources.period_weekly
import trendingai.shared.generated.resources.retry
import trendingai.shared.generated.resources.select_date
import trendingai.shared.generated.resources.sign_in
import trendingai.shared.generated.resources.star_failed
import trendingai.shared.generated.resources.star_need_login
import trendingai.shared.generated.resources.star_success
import trendingai.shared.generated.resources.stars_period
import trendingai.shared.generated.resources.stars_total
import whl.trending.ai.auth.RepoStarService
import whl.trending.ai.auth.globalAuthManager
import whl.trending.ai.core.DateTimeUtils
import whl.trending.ai.core.platform.shareText
import whl.trending.ai.core.platform.trackEvent
import whl.trending.ai.core.platform.trackItemClick
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.data.repository.globalFavoriteRepository
import whl.trending.ai.data.model.FavoriteItem
import whl.trending.ai.data.model.TrendingContributor
import whl.trending.ai.data.model.TrendingRepo
import whl.trending.ai.ui.common.AiSummaryBox
import whl.trending.ai.ui.common.ItemActionMenu
import whl.trending.ai.ui.common.aiShareText
import kotlin.time.Clock

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TrendingScreen(
    onNavigateToDetail: (owner: String, repo: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TrendingViewModel = viewModel { TrendingViewModel() }
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    // GitHub 登录不被支持的平台（如 iOS NoopAuthManager）隐藏 star 入口
    val starEnabled = remember { globalAuthManager.isSupported }

    val msgStarred = stringResource(Res.string.star_success)
    val msgFailed = stringResource(Res.string.star_failed)
    val msgNeedLogin = stringResource(Res.string.star_need_login)
    val actionLogin = stringResource(Res.string.sign_in)
    LaunchedEffect(Unit) {
        viewModel.starEvents.collect { result ->
            when (result) {
                RepoStarService.Result.STARRED -> snackbarHostState.showSnackbar(msgStarred)
                RepoStarService.Result.FAILED -> snackbarHostState.showSnackbar(msgFailed)
                RepoStarService.Result.NEED_LOGIN -> {
                    val action = snackbarHostState.showSnackbar(
                        message = msgNeedLogin,
                        actionLabel = actionLogin,
                    )
                    if (action == SnackbarResult.ActionPerformed) globalAuthManager.signIn("trending_star_snackbar")
                }
                RepoStarService.Result.UNSTARRED -> Unit // 列表页只 star，不触发取消
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        RepoList(
            uiState = uiState,
            modifier = Modifier.fillMaxSize(),
            onRefresh = { viewModel.fetchData(isRefresh = true) },
            onNavigateToDetail = onNavigateToDetail,
            onStarRepo = if (starEnabled) viewModel::starRepo else null,
        )
        SnackbarHost(
            hostState = snackbarHostState,
            // 抬到悬浮底栏之上，否则 star 结果提示会被胶囊压住
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = LocalContentBottomPadding.current),
        )
    }

    if (uiState.showFilterSheet) {
        FilterBottomSheet(
            selectedPeriod = uiState.selectedPeriod,
            selectedLanguage = uiState.selectedLanguage,
            onDismiss = { viewModel.setFilterSheetVisible(false) },
            onConfirm = { period, language ->
                trackEvent(
                    "filter_confirm",
                    mapOf(
                        "period" to period,
                        "language" to language
                    )
                )
                viewModel.updateFilter(period, language)
                viewModel.setFilterSheetVisible(false)
            }
        )
    }

    if (uiState.showHistorySheet) {
        HistoryBottomSheet(
            selectedDate = uiState.selectedDate,
            selectedBatch = uiState.selectedBatch,
            onDismiss = { viewModel.setHistorySheetVisible(false) },
            onConfirm = { date, batch ->
                trackEvent(
                    "history_confirm",
                    mapOf(
                        "date" to (date ?: ""),
                        "batch" to (batch ?: "")
                    )
                )
                viewModel.updateHistoryFilter(date, batch)
                viewModel.setHistorySheetVisible(false)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RepoList(
    uiState: TrendingUiState,
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit,
    onNavigateToDetail: (owner: String, repo: String) -> Unit,
    /** 非空时列表项菜单显示「Star 到 GitHub」，null（不支持登录的平台）则隐藏 */
    onStarRepo: ((TrendingRepo) -> Unit)? = null,
) {
    val state = rememberPullToRefreshState()
    val listState = rememberLazyListState()
    // 切换「只看 New」时滚回顶部：LazyColumn 按 key 会保持可视位置，
    // 关掉开关后画面几乎不变，用户容易以为切换没生效（#36）。
    // 只在开关「真正切换」时滚顶：用 remember 记住上次值，页面重新进入 composition
    // （如从详情返回）时 remember 会以当前值重新初始化 → 判等 → 不滚顶，
    // 从而保留 Nav3 SavedState 恢复的滚动位置（否则每次返回都被 scrollToItem(0) 清零）。
    var lastNewOnly by remember { mutableStateOf(uiState.newOnly) }
    LaunchedEffect(uiState.newOnly) {
        if (uiState.newOnly != lastNewOnly) {
            lastNewOnly = uiState.newOnly
            listState.scrollToItem(0)
        }
    }

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        state = state,
        onRefresh = onRefresh,
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = state,
                isRefreshing = uiState.isRefreshing,
                // 列表铺满全高后指示器的出生点在头部背后，要往下让（静止截图看不出，拉一下才见）
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = LocalContentTopPadding.current),
            )
        },
        modifier = modifier.fillMaxSize()
    ) {
        // newOnly 时仅展示 isNew 的项目（客户端过滤，整张 daily 全语言榜已全量在端上）
        val displayRepos = if (uiState.newOnly) uiState.repos.filter { it.isNew } else uiState.repos
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
                        text = stringResource(Res.string.error_fetch, uiState.error),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onRefresh) {
                        Text(stringResource(Res.string.retry))
                    }
                }
            }

            displayRepos.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(top = LocalContentTopPadding.current),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = stringResource(if (uiState.newOnly) Res.string.no_new_repos else Res.string.no_data))
                }
            }

            else -> {
                val favorites by globalSettingsManager.favorites.collectAsState(emptyList())
                val favoriteUrls = remember(favorites) { favorites.map { it.url }.toSet() }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    // 首条从悬浮头部下面滚出来，末条从悬浮底栏下面滚出来
                    contentPadding = PaddingValues(
                        top = LocalContentTopPadding.current,
                        bottom = LocalContentBottomPadding.current,
                    ),
                ) {
                items(
                    count = displayRepos.size,
                    key = { index -> displayRepos[index].url }
                ) { index ->
                    val repo = displayRepos[index]
                    RepoItem(
                        index = index,
                        repo = repo,
                        since = uiState.since,
                        isFavorite = repo.url in favoriteUrls,
                        onStar = onStarRepo?.let { star -> { star(repo) } },
                        onToggleFavorite = {
                            if (repo.url in favoriteUrls) {
                                globalFavoriteRepository.remove(repo.url)
                            } else {
                                globalFavoriteRepository.add(
                                    FavoriteItem(
                                        url = repo.url,
                                        title = "${repo.author}/${repo.repoName}",
                                        source = "github",
                                        description = repo.description.takeIf { it.isNotBlank() },
                                        summary = repo.aiSummaries.firstOrNull()?.content,
                                        savedAt = Clock.System.now().toEpochMilliseconds(),
                                        externalId = "${repo.author}/${repo.repoName}"
                                    )
                                )
                            }
                        },
                        onClick = {
                            trackItemClick(
                                source = "github",
                                rank = index + 1,
                                title = "${repo.author}/${repo.repoName}"
                            )
                            onNavigateToDetail(repo.author, repo.repoName)
                        }
                    )
                    if (index < displayRepos.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }

                // 抓取时机行：历史快照态显示所看批次的日期，实时态显示最近一次抓取时刻
                item {
                    val stampText = if (uiState.selectedDate != null) {
                        snapshotStampText(uiState.selectedDate, uiState.selectedBatch ?: "am")
                    } else {
                        updateStampText(uiState.capturedAt)?.let {
                            withBatchSuffix(it, uiState.currentBatch)
                        }
                    }
                    stampText?.let { SourceMetaFooter(text = it) }
                }
            }
            }
        }
    }
}

@Composable
private fun RepoItem(index: Int, repo: TrendingRepo, since: String, isFavorite: Boolean, onToggleFavorite: () -> Unit, onClick: () -> Unit, onStar: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .clickable { onClick() }
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            modifier = Modifier.size(28.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "${index + 1}", fontSize = 12.sp, fontWeight = FontWeight.W500)
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${repo.author}/${repo.repoName}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.W500,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (repo.isNew) {
                    NewBadge()
                }
            }
            if (repo.description.isNotBlank()) {
                Text(
                    text = repo.description,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (repo.aiSummaries.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    repo.aiSummaries.forEach { summary ->
                        if (summary.content.isNotEmpty()) {
                            AiSummaryBox(summary.content)
                        }
                    }
                }
            }

            if (repo.builtBy.isNotEmpty()) {
                ContributorAvatars(contributors = repo.builtBy)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    RepoMetadata(repo = repo, since = since)
                }
                val shareSummary = repo.aiSummaries.firstOrNull()?.content
                val shareContent = aiShareText("${repo.author}/${repo.repoName}", shareSummary, repo.url)
                ItemActionMenu(
                    isFavorite = isFavorite,
                    onToggle = onToggleFavorite,
                    onShare = {
                        shareText(shareContent)
                        trackEvent(
                            "share_to_ai",
                            mapOf(
                                "source" to "github",
                                "has_summary" to !shareSummary.isNullOrBlank(),
                                "from" to "list"
                            )
                        )
                    },
                    onStar = onStar,
                )
            }
        }
    }
}

@Composable
private fun ContributorAvatars(contributors: List<TrendingContributor>) {
    val display = contributors.take(5)
    val extra = (contributors.size - 5).coerceAtLeast(0)
    val avatarSize = 20.dp
    val step = 14.dp
    val totalCount = display.size + if (extra > 0) 1 else 0
    val totalWidth = avatarSize + step * (totalCount - 1)

    Box(modifier = Modifier.size(width = totalWidth, height = avatarSize)) {
        display.forEachIndexed { index, contributor ->
            AvatarCircle(
                url = contributor.avatar,
                modifier = Modifier
                    .size(avatarSize)
                    .offset(x = step * index)
                    .zIndex((display.size - index).toFloat())
            )
        }
        if (extra > 0) {
            Box(
                modifier = Modifier
                    .size(avatarSize)
                    .offset(x = step * display.size)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    .zIndex(0f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+$extra",
                    fontSize = 7.sp,
                    fontWeight = FontWeight.W500,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AvatarCircle(url: String, modifier: Modifier = Modifier) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape)
    )
}


@Composable
private fun NewBadge() {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Text(
            text = "NEW",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            fontSize = 9.sp,
            fontWeight = FontWeight.W700,
            letterSpacing = 0.5.sp,
            lineHeight = 11.sp
        )
    }
}

@Composable
private fun RepoMetadata(repo: TrendingRepo, since: String) {
    val periodLabel = when (since) {
        "daily" -> stringResource(Res.string.period_daily)
        "weekly" -> stringResource(Res.string.period_weekly)
        "monthly" -> stringResource(Res.string.period_monthly)
        else -> since
    }
    val starsTotal = stringResource(Res.string.stars_total, DateTimeUtils.formatNumber(repo.stars))
    val starsPeriod = stringResource(Res.string.stars_period, periodLabel, DateTimeUtils.formatNumber(repo.currentPeriodStars))
    val metadataText = buildString {
        if (!repo.language.isNullOrEmpty()) append("${repo.language} · ")
        append("$starsTotal · $starsPeriod")
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(8.dp),
            shape = CircleShape,
            color = repo.languageColor?.toColorOrNull() ?: MaterialTheme.colorScheme.outline
        ) {}
        Text(
            text = metadataText,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FilterBottomSheet(
    selectedPeriod: String,
    selectedLanguage: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    val periods = listOf("daily", "weekly", "monthly")
    val languages = listOf("all", "javascript", "java", "go", "rust", "typescript", "c++", "c", "swift", "kotlin")

    var tempPeriod by remember { mutableStateOf(selectedPeriod) }
    var tempLanguage by remember { mutableStateOf(selectedLanguage) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // 这里曾挂一个「更新机制」InfoDialog：更新节奏与筛选周期/语言无关，位置本就错了，
    // 且那份文案已并入「数据来源与更新」页（从列表头部的抓取时机条进入）。
    TrendingBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        title = stringResource(Res.string.filter_options),
    ) {

            Text(
                text = stringResource(Res.string.filter_period),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                periods.forEachIndexed { index, period ->
                    val label = when (period) {
                        "daily" -> stringResource(Res.string.period_daily)
                        "weekly" -> stringResource(Res.string.period_weekly)
                        "monthly" -> stringResource(Res.string.period_monthly)
                        else -> period
                    }
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = periods.size),
                        onClick = { tempPeriod = period },
                        selected = tempPeriod == period
                    ) { Text(label) }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(Res.string.filter_language),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                languages.forEach { language ->
                    FilterChip(
                        selected = tempLanguage == language,
                        onClick = { tempLanguage = language },
                        label = { Text(language.replaceFirstChar { it.uppercase() }) },
                        leadingIcon = null
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                androidx.compose.material3.OutlinedButton(
                    onClick = {
                        onConfirm("daily", "all")
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(Res.string.filter_reset))
                }

                Button(
                    onClick = { onConfirm(tempPeriod, tempLanguage) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(Res.string.filter_done))
                }
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryBottomSheet(
    selectedDate: String?,
    selectedBatch: String?,
    onDismiss: () -> Unit,
    onConfirm: (String?, String?) -> Unit
) {
    var tempDate by remember { mutableStateOf(selectedDate ?: "") }
    var tempBatch by remember { mutableStateOf(selectedBatch ?: "am") }
    var showHelpDialog by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val datePickerState = rememberDatePickerState()
    var showDatePicker by remember { mutableStateOf(false) }

    if (showHelpDialog) {
        InfoDialog(
            title = stringResource(Res.string.history_info_title),
            content = stringResource(Res.string.history_info_content),
            onDismiss = { showHelpDialog = false }
        )
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val selected = datePickerState.selectedDateMillis
                    if (selected != null) {
                        tempDate = DateTimeUtils.formatEpochMillisToDate(selected)
                    }
                    showDatePicker = false
                }) { Text(stringResource(Res.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(Res.string.cancel)) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    TrendingBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        title = stringResource(Res.string.history_trending),
        titleAction = {
            IconButton(onClick = { showHelpDialog = true }) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = stringResource(Res.string.action_help),
                )
            }
        },
    ) {

            Text(
                text = stringResource(Res.string.history_date),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = tempDate,
                    onValueChange = { },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(Res.string.click_to_select_date)) },
                    trailingIcon = {
                        Icon(Icons.Default.DateRange, contentDescription = stringResource(Res.string.select_date))
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    enabled = false,
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                // 覆盖一个透明层处理点击，因为 disabled 的 TextField 无法接收点击事件
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { showDatePicker = true }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(Res.string.history_batch),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val amLabel = stringResource(Res.string.batch_am)
                val pmLabel = stringResource(Res.string.batch_pm)
                val batches = listOf("am" to amLabel, "pm" to pmLabel)
                batches.forEachIndexed { index, (batchValue, label) ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = batches.size),
                        onClick = { tempBatch = batchValue },
                        selected = tempBatch == batchValue
                    ) { Text(label) }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                androidx.compose.material3.OutlinedButton(
                    onClick = {
                        onConfirm(null, null)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(Res.string.filter_reset))
                }

                Button(
                    onClick = {
                        val finalDate = tempDate.trim().takeIf { it.isNotEmpty() }
                        val finalBatch = if (finalDate != null) tempBatch else null
                        onConfirm(finalDate, finalBatch)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(Res.string.filter_done))
                }
            }
    }
}

private fun String.toColorOrNull(): Color? {
    val hex = this.removePrefix("#")
    return if (hex.length == 6) {
        runCatching {
            Color((hex.toLong(16) or 0xFF000000L).toInt())
        }.getOrNull()
    } else {
        null
    }
}
