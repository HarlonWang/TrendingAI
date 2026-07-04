package whl.trending.ai.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FiberNew
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.GitHub_Invertocat_Black
import trendingai.shared.generated.resources.GitHub_Invertocat_White
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.picks_title
import trendingai.shared.generated.resources.hackernews_title
import trendingai.shared.generated.resources.producthunt_title
import trendingai.shared.generated.resources.app_name
import trendingai.shared.generated.resources.filter_new_only
import trendingai.shared.generated.resources.new_only_hint
import trendingai.shared.generated.resources.icon_producthunt_dark
import trendingai.shared.generated.resources.icon_producthunt_light
import trendingai.shared.generated.resources.batch_am
import trendingai.shared.generated.resources.batch_pm
import trendingai.shared.generated.resources.history_trending
import trendingai.shared.generated.resources.period_daily
import trendingai.shared.generated.resources.period_monthly
import trendingai.shared.generated.resources.period_weekly
import trendingai.shared.generated.resources.settings
import trendingai.shared.generated.resources.profile_title
import trendingai.shared.generated.resources.sign_in
import whl.trending.ai.auth.AuthState
import whl.trending.ai.auth.globalAuthManager
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.data.repository.UserRepository
import whl.trending.ai.ui.feed.FeedScreen
import whl.trending.ai.ui.feed.FeedViewModel
import whl.trending.ai.ui.picks.PicksScreen
import whl.trending.ai.ui.picks.PicksViewModel
import whl.trending.ai.core.platform.trackEvent
import whl.trending.ai.ui.trending.TrendingScreen
import whl.trending.ai.ui.trending.TrendingViewModel
import kotlin.time.Clock

enum class HomeTab {
    GitHub, HackerNews, ProductHunt, Picks
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToDetail: (owner: String, repo: String) -> Unit,
    onNavigateToChat: () -> Unit = {},
    onOpenUrl: (url: String) -> Unit = {},
    onNavigateToProfile: () -> Unit = {}
) {
    var selectedTabName by rememberSaveable { mutableStateOf(HomeTab.GitHub.name) }
    val selectedTab = HomeTab.valueOf(selectedTabName)
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
    val hnViewModel: FeedViewModel? = if (selectedTab == HomeTab.HackerNews) {
        viewModel(key = "hackernews") { FeedViewModel("hackernews") }
    } else null
    val phViewModel: FeedViewModel? = if (selectedTab == HomeTab.ProductHunt) {
        viewModel(key = "producthunt") { FeedViewModel("producthunt") }
    } else null

    val authManager = globalAuthManager
    val authState by authManager.authState.collectAsState()
    val userAvatarUrl by globalSettingsManager.userAvatarUrl.collectAsState(null)
    val userRepository = remember { UserRepository() }

    // 应用启动且已登录：服务端建档/刷新 last_login_at + 同步头像（幂等，失败静默）
    LaunchedEffect(authState) {
        val state = authState
        if (state is AuthState.LoggedIn) {
            userRepository.syncMe(authManager.getAccessToken())
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            when (selectedTab) {
                HomeTab.GitHub -> TrendingTopBar(
                    selectedPeriod = trendingUiState.selectedPeriod,
                    selectedLanguage = trendingUiState.selectedLanguage,
                    selectedDate = trendingUiState.selectedDate,
                    selectedBatch = trendingUiState.selectedBatch,
                    newOnly = trendingUiState.newOnly,
                    onToggleNewOnly = { trendingViewModel.toggleNewOnly() },
                    scrollBehavior = scrollBehavior,
                    onTitleClick = { showFilterSheet = true },
                    onHistoryClick = { showHistorySheet = true },
                    onNavigateToSettings = onNavigateToSettings,
                    showAuthEntry = authManager.isSupported,
                    authState = authState,
                    userAvatarUrl = userAvatarUrl,
                    onProfileClick = {
                        if (authState is AuthState.LoggedIn) onNavigateToProfile() else authManager.signIn()
                    },
                )
                HomeTab.Picks -> PicksTopBar(
                    date = picksUiState?.picks?.metadata?.date,
                    scrollBehavior = scrollBehavior,
                    onNavigateToSettings = onNavigateToSettings
                )
                HomeTab.HackerNews -> {
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
                        scrollBehavior = scrollBehavior,
                        onNavigateToSettings = onNavigateToSettings
                    )
                }
                HomeTab.ProductHunt -> {
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
                        scrollBehavior = scrollBehavior,
                        onNavigateToSettings = onNavigateToSettings
                    )
                }
            }
        },
        floatingActionButton = {
            if (whl.trending.ai.chat.globalChatScreen != null) {
                FloatingActionButton(onClick = onNavigateToChat) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "AI Assistant"
                    )
                }
            }
        },
        bottomBar = {
            // 双击当前 tab 触发下拉刷新（#38）：仅当两次点击都落在已选中的 tab 上才算，
            // 双击未选中的 tab 只切换不刷新（切换会重置计时）
            val doubleTapMillis = LocalViewConfiguration.current.doubleTapTimeoutMillis
            var lastTapTime by remember { mutableStateOf(0L) }
            val refreshCurrentTab = {
                trackEvent("tab_double_tap_refresh", mapOf("tab" to selectedTab.name.lowercase()))
                when (selectedTab) {
                    HomeTab.GitHub -> trendingViewModel.fetchData(isRefresh = true)
                    HomeTab.HackerNews -> hnViewModel?.refresh()
                    HomeTab.ProductHunt -> phViewModel?.refresh()
                    HomeTab.Picks -> picksViewModel?.refresh()
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
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == HomeTab.GitHub,
                    onClick = { switchTo(HomeTab.GitHub) },
                    icon = { Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = "GitHub") },
                    label = { Text("GitHub") }
                )
                NavigationBarItem(
                    selected = selectedTab == HomeTab.HackerNews,
                    onClick = { switchTo(HomeTab.HackerNews) },
                    icon = { Icon(HackerNewsYIcon, contentDescription = stringResource(Res.string.hackernews_title)) },
                    label = { Text("HN") }
                )
                NavigationBarItem(
                    selected = selectedTab == HomeTab.ProductHunt,
                    onClick = { switchTo(HomeTab.ProductHunt) },
                    icon = { Icon(ProductHuntPIcon, contentDescription = stringResource(Res.string.producthunt_title)) },
                    label = { Text("PH") }
                )
                NavigationBarItem(
                    selected = selectedTab == HomeTab.Picks,
                    onClick = { switchTo(HomeTab.Picks) },
                    icon = { Icon(Icons.Default.Star, contentDescription = stringResource(Res.string.picks_title)) },
                    label = { Text(stringResource(Res.string.picks_title)) }
                )
            }
        }
    ) { innerPadding ->
        when (selectedTab) {
            HomeTab.GitHub -> TrendingScreen(
                onNavigateToDetail = onNavigateToDetail,
                showFilterSheet = showFilterSheet,
                onDismissFilterSheet = { showFilterSheet = false },
                showHistorySheet = showHistorySheet,
                onDismissHistorySheet = { showHistorySheet = false },
                modifier = Modifier.padding(innerPadding),
                viewModel = trendingViewModel
            )
            HomeTab.HackerNews -> FeedScreen(
                modifier = Modifier.padding(innerPadding),
                viewModel = hnViewModel!!,
                onOpenUrl = onOpenUrl
            )
            HomeTab.ProductHunt -> FeedScreen(
                modifier = Modifier.padding(innerPadding),
                viewModel = phViewModel!!,
                onOpenUrl = onOpenUrl
            )
            HomeTab.Picks -> PicksScreen(
                onNavigateToDetail = onNavigateToDetail,
                onOpenUrl = onOpenUrl,
                modifier = Modifier.padding(innerPadding),
                viewModel = picksViewModel!!
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
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior,
    onTitleClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onNavigateToSettings: () -> Unit,
    showAuthEntry: Boolean,
    authState: AuthState,
    userAvatarUrl: String?,
    onProfileClick: () -> Unit,
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val periodLabel = when (selectedPeriod) {
        "daily" -> stringResource(Res.string.period_daily)
        "weekly" -> stringResource(Res.string.period_weekly)
        "monthly" -> stringResource(Res.string.period_monthly)
        else -> selectedPeriod
    }

    TopAppBar(
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
        scrollBehavior = scrollBehavior,
        navigationIcon = {
            Box(modifier = Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(
                        if (isDarkTheme) Res.drawable.GitHub_Invertocat_White
                        else Res.drawable.GitHub_Invertocat_Black
                    ),
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
            if (showAuthEntry) {
                IconButton(onClick = onProfileClick, enabled = authState !is AuthState.LoggingIn) {
                    when {
                        authState is AuthState.LoggedIn && userAvatarUrl != null -> AsyncImage(
                            model = userAvatarUrl,
                            contentDescription = stringResource(Res.string.profile_title),
                            modifier = Modifier.size(28.dp).clip(CircleShape)
                        )
                        authState is AuthState.LoggingIn -> LoadingIndicator(
                            modifier = Modifier.size(24.dp)
                        )
                        else -> Icon(
                            Icons.Default.AccountCircle,
                            contentDescription = stringResource(Res.string.sign_in)
                        )
                    }
                }
            }
            IconButton(onClick = onHistoryClick) {
                Icon(Icons.Default.DateRange, contentDescription = stringResource(Res.string.history_trending))
            }
            IconButton(onClick = onNavigateToSettings) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(Res.string.settings))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PicksTopBar(
    date: String?,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior,
    onNavigateToSettings: () -> Unit
) {
    TopAppBar(
        scrollBehavior = scrollBehavior,
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
        actions = {
            IconButton(onClick = onNavigateToSettings) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(Res.string.settings))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedTopBar(
    title: String,
    navigationIcon: @Composable () -> Unit,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior,
    onNavigateToSettings: () -> Unit
) {
    TopAppBar(
        scrollBehavior = scrollBehavior,
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
        actions = {
            IconButton(onClick = onNavigateToSettings) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(Res.string.settings))
            }
        }
    )
}
