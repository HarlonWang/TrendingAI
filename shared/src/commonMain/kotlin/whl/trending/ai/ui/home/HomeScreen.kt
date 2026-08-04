package whl.trending.ai.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FiberNew
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.app_name
import trendingai.shared.generated.resources.batch_am
import trendingai.shared.generated.resources.batch_pm
import trendingai.shared.generated.resources.chat_title
import trendingai.shared.generated.resources.filter_new_only
import trendingai.shared.generated.resources.hackernews_title
import trendingai.shared.generated.resources.history_trending
import trendingai.shared.generated.resources.icon_producthunt_dark
import trendingai.shared.generated.resources.icon_producthunt_light
import trendingai.shared.generated.resources.new_only_hint
import trendingai.shared.generated.resources.period_daily
import trendingai.shared.generated.resources.period_monthly
import trendingai.shared.generated.resources.period_weekly
import trendingai.shared.generated.resources.picks_title
import trendingai.shared.generated.resources.producthunt_title
import trendingai.shared.generated.resources.me_title
import trendingai.shared.generated.resources.trending_title
import whl.trending.ai.chat.globalChatScreen
import whl.trending.ai.core.platform.trackEvent
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.data.repository.ChatModelsProvider
import whl.trending.ai.ui.common.LocalContentBottomPadding
import whl.trending.ai.ui.common.TrendingScaffold
import whl.trending.ai.ui.common.TrendingTopAppBar
import whl.trending.ai.ui.digest.DigestPage
import whl.trending.ai.ui.feed.FeedScreen
import whl.trending.ai.ui.feed.FeedViewModel
import whl.trending.ai.ui.picks.PicksScreen
import whl.trending.ai.ui.picks.PicksViewModel
import whl.trending.ai.ui.profile.ProfileScreen
import whl.trending.ai.ui.trending.TrendingScreen
import whl.trending.ai.ui.trending.TrendingViewModel
import kotlin.time.Clock

/**
 * 首页骨架：底栏四项（Trending / Picks / AI 对话 / 我的）。
 *
 * Trending 内含 GitHub / Hacker News / Product Hunt 三个子源，用 [PrimaryTabRow] 切换——
 * 只点击、不横滑：三个源各自有下拉刷新与横向可滚内容，再叠一层横向手势会互相抢。
 *
 * AI 对话是入口不是落点：点它直接推全屏聊天页，底栏选中态仍留在原 tab（见 [HomeTab.Chat]）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToDetail: (owner: String, repo: String) -> Unit,
    onNavigateToChat: () -> Unit = {},
    onOpenUrl: (url: String) -> Unit = {},
    onNavigateToSubscribe: () -> Unit = {},
    onOpenDigest: (DigestPage) -> Unit = {},
    onNavigateToGithubProfile: () -> Unit = {},
    onNavigateToFavorites: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
) {
    // 冷启动进入设置页选的默认 tab；仅初始值，会话内切换与 rememberSaveable 恢复不受影响
    var selectedTabName by rememberSaveable {
        mutableStateOf(HomeTab.defaultFromName(globalSettingsManager.currentDefaultHomeTab()).name)
    }
    val selectedTab = HomeTab.fromNameOrDefault(selectedTabName)

    // 子源与 tab 不同：每次切换都回写，冷启动回到上次看的那个源
    var selectedSourceName by rememberSaveable {
        mutableStateOf(TrendingSource.fromNameOrDefault(globalSettingsManager.currentTrendingSource()).name)
    }
    val selectedSource = TrendingSource.fromNameOrDefault(selectedSourceName)

    // 组合树外的切 tab 请求（通知点击深链等）：置位状态可跨冷启动等到这里再消费
    LaunchedEffect(Unit) {
        HomeTabRequest.pending.collect { tab ->
            if (tab != null) {
                selectedTabName = tab.name
                HomeTabRequest.consume()
            }
        }
    }
    var showFilterSheet by rememberSaveable { mutableStateOf(false) }
    var showHistorySheet by rememberSaveable { mutableStateOf(false) }

    val trendingViewModel: TrendingViewModel = viewModel { TrendingViewModel() }
    val trendingUiState by trendingViewModel.uiState.collectAsState()

    // Picks tab 被选中时才创建 ViewModel，topBar 和 content 共享同一实例
    val picksViewModel: PicksViewModel? = if (selectedTab == HomeTab.Picks) {
        viewModel { PicksViewModel() }
    } else null
    val picksUiState = picksViewModel?.uiState?.collectAsState()?.value

    // HN / PH 同样按需创建；提升到这里是为了 bottomBar 双击刷新能拿到同一实例
    val onTrendingTab = selectedTab == HomeTab.Trending
    val hnViewModel: FeedViewModel? = if (onTrendingTab && selectedSource == TrendingSource.HackerNews) {
        viewModel(key = "hackernews") { FeedViewModel("hackernews") }
    } else null
    val phViewModel: FeedViewModel? = if (onTrendingTab && selectedSource == TrendingSource.ProductHunt) {
        viewModel(key = "producthunt") { FeedViewModel("producthunt") }
    } else null

    // syncMe（建档 + 头像/GitHub 身份/isPro 缓存）不在此触发——已随收藏同步一起挂在 App 根部
    // （见 App.kt）：登录常发生在「我的」tab，Home 的 LaunchedEffect 彼时仍在，但登录也可能
    // 发生在被推到栈顶的子页，挂这里会漏掉那条路径（isPro 不回写，Pro 用户被当免费档）。

    // 预热聊天模型目录：让选择器 chip 在用户首次进入 chat 前就绪，避免冷首拉导致 chip 迟迟不出现。
    LaunchedEffect(Unit) {
        if (globalChatScreen != null) {
            ChatModelsProvider.warmUp(this)
        }
    }

    TrendingScaffold(
        topBar = {
            when (selectedTab) {
                HomeTab.Trending -> when (selectedSource) {
                    TrendingSource.GitHub -> TrendingTopBar(
                        selectedPeriod = trendingUiState.selectedPeriod,
                        selectedLanguage = trendingUiState.selectedLanguage,
                        selectedDate = trendingUiState.selectedDate,
                        selectedBatch = trendingUiState.selectedBatch,
                        newOnly = trendingUiState.newOnly,
                        onToggleNewOnly = { trendingViewModel.toggleNewOnly() },
                        onTitleClick = { showFilterSheet = true },
                        onHistoryClick = { showHistorySheet = true },
                    )
                    TrendingSource.HackerNews -> {
                        val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
                        FeedTopBar(
                            title = stringResource(Res.string.hackernews_title),
                            navigationIcon = {
                                Icon(
                                    imageVector = hackerNewsIcon(if (isDark) HackerNewsOrange else Color.Black),
                                    contentDescription = "Hacker News",
                                    modifier = Modifier.size(24.dp),
                                    tint = Color.Unspecified
                                )
                            },
                        )
                    }
                    TrendingSource.ProductHunt -> {
                        val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
                        FeedTopBar(
                            title = stringResource(Res.string.producthunt_title),
                            navigationIcon = {
                                Icon(
                                    painter = painterResource(
                                        if (isDark) Res.drawable.icon_producthunt_dark
                                        else Res.drawable.icon_producthunt_light
                                    ),
                                    contentDescription = "Product Hunt",
                                    modifier = Modifier.size(24.dp),
                                    tint = Color.Unspecified
                                )
                            },
                        )
                    }
                }
                HomeTab.Picks -> PicksTopBar(date = picksUiState?.picks?.metadata?.date)
                HomeTab.Me -> TrendingTopAppBar(
                    title = {
                        Text(
                            text = stringResource(Res.string.me_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    },
                )
                // 选中态永不为 Chat（点击即推聊天页），这里只是穷尽 when
                HomeTab.Chat -> Unit
            }
        },
        // 底栏是浮在内容之上的胶囊，不占 Scaffold 的 bottomBar 槽位——占了内容就被顶上去，
        // 拿不到「内容从底栏下面穿过去」的观感。
    ) { innerPadding ->
        // 只取顶部：底部留给悬浮底栏自己算（见 LocalContentBottomPadding），
        // 内容层不做底部 padding，列表才能滚到底栏之下。
        val contentModifier = Modifier.padding(top = innerPadding.calculateTopPadding())
        val barBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val contentBottomPadding = barBottomInset + FloatingBarHeight + FloatingBarBottomMargin * 2

        val doubleTapMillis = LocalViewConfiguration.current.doubleTapTimeoutMillis
        var lastTapTime by remember { mutableStateOf(0L) }
        // 双击当前 tab 触发下拉刷新（#38）：仅当两次点击都落在已选中的 tab 上才算，
        // 双击未选中的 tab 只切换不刷新（切换会重置计时）
        val refreshCurrentTab = {
            trackEvent(
                "tab_double_tap_refresh",
                mapOf("tab" to selectedTab.name.lowercase()),
            )
            when (selectedTab) {
                HomeTab.Trending -> when (selectedSource) {
                    TrendingSource.GitHub -> trendingViewModel.fetchData(isRefresh = true)
                    TrendingSource.HackerNews -> hnViewModel?.refresh()
                    TrendingSource.ProductHunt -> phViewModel?.refresh()
                }
                HomeTab.Picks -> picksViewModel?.refresh()
                // 「我的」自带下拉刷新，Chat 不是落点：都不参与双击刷新
                HomeTab.Me, HomeTab.Chat -> Unit
            }
        }
        val switchTo = { tab: HomeTab ->
            if (selectedTab != tab) {
                trackEvent("tab_switch", mapOf("tab" to tab.name.lowercase()))
                selectedTabName = tab.name
                lastTapTime = 0L
            } else {
                val now = Clock.System.now().toEpochMilliseconds()
                if (now - lastTapTime <= doubleTapMillis) {
                    refreshCurrentTab()
                    lastTapTime = 0L // 已触发一次，三连击不重复刷新
                } else {
                    lastTapTime = now
                }
            }
        }

        val barItems = buildList {
            add(
                HomeBarItem(
                    key = HomeTab.Trending,
                    iconSelected = Icons.Filled.Whatshot,
                    iconUnselected = Icons.Outlined.Whatshot,
                    label = stringResource(Res.string.trending_title),
                    selected = selectedTab == HomeTab.Trending,
                    onClick = { switchTo(HomeTab.Trending) },
                )
            )
            add(
                HomeBarItem(
                    key = HomeTab.Picks,
                    iconSelected = Icons.Filled.Star,
                    iconUnselected = Icons.Outlined.StarOutline,
                    label = stringResource(Res.string.picks_title),
                    selected = selectedTab == HomeTab.Picks,
                    onClick = { switchTo(HomeTab.Picks) },
                )
            )
            // 聊天未接入的平台（iOS）隐藏该项，底栏退化成三项
            if (globalChatScreen != null) {
                add(
                    HomeBarItem(
                        key = HomeTab.Chat,
                        iconSelected = Icons.Filled.AutoAwesome,
                        iconUnselected = Icons.Outlined.AutoAwesome,
                        label = stringResource(Res.string.chat_title),
                        // 选中态不给它：点击只是推一页，回来后仍在原 tab
                        selected = false,
                        onClick = {
                            trackEvent("tab_switch", mapOf("tab" to HomeTab.Chat.name.lowercase()))
                            onNavigateToChat()
                        },
                    )
                )
            }
            add(
                HomeBarItem(
                    key = HomeTab.Me,
                    iconSelected = Icons.Filled.AccountCircle,
                    iconUnselected = Icons.Outlined.AccountCircle,
                    label = stringResource(Res.string.me_title),
                    selected = selectedTab == HomeTab.Me,
                    onClick = { switchTo(HomeTab.Me) },
                )
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalContentBottomPadding provides contentBottomPadding) {
                when (selectedTab) {
                    HomeTab.Trending -> Column(modifier = contentModifier) {
                        TrendingSourceTabs(
                            selected = selectedSource,
                            onSelect = { source ->
                                if (source != selectedSource) {
                                    trackEvent(
                                        "trending_source_switch",
                                        mapOf("source" to source.name.lowercase()),
                                    )
                                    selectedSourceName = source.name
                                    globalSettingsManager.setTrendingSource(source.name)
                                }
                            },
                        )
                        when (selectedSource) {
                            TrendingSource.GitHub -> TrendingScreen(
                                onNavigateToDetail = onNavigateToDetail,
                                showFilterSheet = showFilterSheet,
                                onDismissFilterSheet = { showFilterSheet = false },
                                showHistorySheet = showHistorySheet,
                                onDismissHistorySheet = { showHistorySheet = false },
                                viewModel = trendingViewModel
                            )
                            TrendingSource.HackerNews -> FeedScreen(
                                viewModel = hnViewModel!!,
                                onOpenUrl = onOpenUrl,
                                onOpenDigest = onOpenDigest
                            )
                            TrendingSource.ProductHunt -> FeedScreen(
                                viewModel = phViewModel!!,
                                onOpenUrl = onOpenUrl
                            )
                        }
                    }
                    HomeTab.Picks -> PicksScreen(
                        onNavigateToDetail = onNavigateToDetail,
                        onOpenUrl = onOpenUrl,
                        onNavigateToSubscribe = onNavigateToSubscribe,
                        modifier = contentModifier,
                        viewModel = picksViewModel!!,
                        onOpenDigest = onOpenDigest
                    )
                    HomeTab.Me -> ProfileScreen(
                        modifier = contentModifier,
                        onNavigateToGithubProfile = onNavigateToGithubProfile,
                        onNavigateToFavorites = onNavigateToFavorites,
                    )
                    HomeTab.Chat -> Unit
                }
            }

            HomeFloatingBar(
                items = barItems,
                onOpenSettings = onNavigateToSettings,
                onOpenAbout = onNavigateToAbout,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = barBottomInset + FloatingBarBottomMargin),
            )
        }
    }
}

/** Trending 的三源子 tab。文案用各源全称，图标交给顶栏——一行三项不必再塞图标。 */
@Composable
private fun TrendingSourceTabs(
    selected: TrendingSource,
    onSelect: (TrendingSource) -> Unit,
) {
    PrimaryTabRow(selectedTabIndex = selected.ordinal) {
        TrendingSource.entries.forEach { source ->
            val label = when (source) {
                TrendingSource.GitHub -> "GitHub"
                TrendingSource.HackerNews -> stringResource(Res.string.hackernews_title)
                TrendingSource.ProductHunt -> stringResource(Res.string.producthunt_title)
            }
            Tab(
                selected = source == selected,
                onClick = { onSelect(source) },
                text = { Text(label, style = MaterialTheme.typography.titleSmall) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TrendingTopBar(
    selectedPeriod: String,
    selectedLanguage: String,
    selectedDate: String?,
    selectedBatch: String?,
    newOnly: Boolean,
    onToggleNewOnly: () -> Unit,
    onTitleClick: () -> Unit,
    onHistoryClick: () -> Unit,
) {
    val periodLabel = when (selectedPeriod) {
        "daily" -> stringResource(Res.string.period_daily)
        "weekly" -> stringResource(Res.string.period_weekly)
        "monthly" -> stringResource(Res.string.period_monthly)
        else -> selectedPeriod
    }

    TrendingTopAppBar(
        title = {
            Column(
                modifier = Modifier
                    .clickable { onTitleClick() }
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(Res.string.app_name),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp).padding(start = 4.dp)
                    )
                }

                val langLabel = selectedLanguage.replaceFirstChar { it.uppercase() }
                val subTitle = buildString {
                    append("$periodLabel · $langLabel")
                    if (!selectedDate.isNullOrEmpty()) {
                        val batchLabel = if (selectedBatch == "am") stringResource(Res.string.batch_am) else stringResource(Res.string.batch_pm)
                        append(" · $selectedDate ($batchLabel)")
                    }
                }

                Text(
                    text = subTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        navigationIcon = {
            Box(modifier = Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                Icon(
                    painter = githubLogoPainter(),
                    contentDescription = "GitHub",
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        actions = {
            // 「只看 New」仅 daily 全语言榜有效，其他视图（按语言/周/月）隐藏该按钮
            if (selectedPeriod == "daily" && selectedLanguage == "all") {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                        TooltipAnchorPosition.Above
                    ),
                    tooltip = {
                        RichTooltip(
                            title = { Text(stringResource(Res.string.filter_new_only)) },
                        ) {
                            Text(stringResource(Res.string.new_only_hint))
                        }
                    },
                    state = rememberTooltipState(),
                ) {
                    FilledIconToggleButton(
                        checked = newOnly,
                        onCheckedChange = { onToggleNewOnly() },
                        colors = IconButtonDefaults.filledIconToggleButtonColors(
                            containerColor = Color.Transparent,                        // 未选中：无底色，与其他图标一致
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            checkedContainerColor = MaterialTheme.colorScheme.primary,  // 选中：品牌紫实心
                            checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Icon(
                            Icons.Default.FiberNew,
                            contentDescription = stringResource(Res.string.filter_new_only)
                        )
                    }
                }
            }
            IconButton(onClick = onHistoryClick) {
                Icon(Icons.Default.DateRange, contentDescription = stringResource(Res.string.history_trending))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PicksTopBar(date: String?) {
    TrendingTopAppBar(
        title = {
            Column {
                Text(
                    text = stringResource(Res.string.picks_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = buildString {
                        append("GitHub · Hacker News · Product Hunt")
                        if (!date.isNullOrBlank()) append(" · $date")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedTopBar(
    title: String,
    navigationIcon: @Composable () -> Unit,
) {
    TrendingTopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
        },
        navigationIcon = {
            Box(modifier = Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                navigationIcon()
            }
        },
    )
}
