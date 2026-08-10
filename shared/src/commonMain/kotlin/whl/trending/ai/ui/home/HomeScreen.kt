package whl.trending.ai.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import trendingai.shared.generated.resources.hackernews_title
import trendingai.shared.generated.resources.home_title
import trendingai.shared.generated.resources.icon_producthunt_dark
import trendingai.shared.generated.resources.icon_producthunt_light
import trendingai.shared.generated.resources.me_title
import trendingai.shared.generated.resources.picks_title
import trendingai.shared.generated.resources.producthunt_title
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
 * 首页骨架：底栏胶囊三项（首页 / Picks / 我的）+ 右侧独立的 AI 对话 FAB。
 *
 * 首页内含 GitHub / Hacker News / Product Hunt 三个子源，用 [TrendingSourceTabs] 切换——
 * 只点击、不横滑：三个源各自有下拉刷新与横向可滚内容，再叠一层横向手势会互相抢。
 *
 * AI 对话是入口不是落点：点 FAB 直接推全屏聊天页，不占 tab 选中态（缘由见
 * [HomeFloatingBar] 的 KDoc；[HomeTab.Chat] 仅为深链等历史路径保留）。
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
    val onHomeTab = selectedTab == HomeTab.Home
    val hnViewModel: FeedViewModel? = if (onHomeTab && selectedSource == TrendingSource.HackerNews) {
        viewModel(key = "hackernews") { FeedViewModel("hackernews") }
    } else null
    val phViewModel: FeedViewModel? = if (onHomeTab && selectedSource == TrendingSource.ProductHunt) {
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

    // 首页进设置页统一记一条事件，entry 区分来源。1.1 起只剩 topbar（顶栏常驻齿轮）——
    // 底栏「⋯」菜单随 Chat FAB 上位而删除，历史数据里的 entry=more 即那条已死路径。
    val openSettings = { entry: String ->
        trackEvent("home_open_settings", mapOf("entry" to entry))
        onNavigateToSettings()
    }

    TrendingScaffold(
        topBar = {
            when (selectedTab) {
                HomeTab.Home -> when (selectedSource) {
                    TrendingSource.GitHub -> TrendingTopBar(
                        selectedPeriod = trendingUiState.selectedPeriod,
                        selectedLanguage = trendingUiState.selectedLanguage,
                        selectedDate = trendingUiState.selectedDate,
                        selectedBatch = trendingUiState.selectedBatch,
                        newOnly = trendingUiState.newOnly,
                        onToggleNewOnly = { trendingViewModel.toggleNewOnly() },
                        onTitleClick = { showFilterSheet = true },
                        onHistoryClick = { showHistorySheet = true },
                        onSettingsClick = { openSettings("topbar") },
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
                            onSettingsClick = { openSettings("topbar") },
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
                            onSettingsClick = { openSettings("topbar") },
                        )
                    }
                }
                HomeTab.Picks -> PicksTopBar(
                    date = picksUiState?.picks?.metadata?.date,
                    onSettingsClick = { openSettings("topbar") },
                )
                HomeTab.Me -> TrendingTopAppBar(
                    title = {
                        Text(
                            text = stringResource(Res.string.me_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    },
                    actions = { SettingsAction(onClick = { openSettings("topbar") }) },
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
                HomeTab.Home -> when (selectedSource) {
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
                    key = HomeTab.Home,
                    iconSelected = Icons.Filled.Home,
                    iconUnselected = Icons.Outlined.Home,
                    label = stringResource(Res.string.home_title),
                    selected = selectedTab == HomeTab.Home,
                    onClick = { switchTo(HomeTab.Home) },
                )
            )
            add(
                HomeBarItem(
                    key = HomeTab.Picks,
                    iconSelected = KidStarFilled,
                    iconUnselected = KidStarOutlined,
                    label = stringResource(Res.string.picks_title),
                    selected = selectedTab == HomeTab.Picks,
                    onClick = { switchTo(HomeTab.Picks) },
                )
            )
            add(
                HomeBarItem(
                    key = HomeTab.Me,
                    iconSelected = Icons.Filled.Person,
                    iconUnselected = Icons.Outlined.Person,
                    label = stringResource(Res.string.me_title),
                    selected = selectedTab == HomeTab.Me,
                    onClick = { switchTo(HomeTab.Me) },
                )
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalContentBottomPadding provides contentBottomPadding) {
                // 切 tab 的方向性转场：新页从前进方向滑入 1/8 屏、旧页往反方向滑出，各配 200ms
                // 淡入淡出。位移量、时长与方向判定照搬 Echo 的 NavHost enter/exitTransition；
                // 差别只在承载方式——它的 tab 是 NavHost 目的地，我们的 tab 是状态，等价物是
                // AnimatedContent。
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        if (targetState.ordinal > initialState.ordinal) {
                            slideInHorizontally { it / 8 } + fadeIn(tween(200)) togetherWith
                                slideOutHorizontally { -it / 8 } + fadeOut(tween(200))
                        } else {
                            slideInHorizontally { -it / 8 } + fadeIn(tween(200)) togetherWith
                                slideOutHorizontally { it / 8 } + fadeOut(tween(200))
                        }
                    },
                    label = "homeTabContent",
                ) { tab ->
                    // 各页在这里自取 ViewModel，不用外面那几个可空的：转场期间旧页仍在组合树里，
                    // 而外面的实例是「当前选中才创建」，此刻已是 null，直接用会崩。
                    // viewModel() 取的是同一个 ViewModelStore 里的同一实例，不会多创建。
                    when (tab) {
                        HomeTab.Home -> Column(modifier = contentModifier) {
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
                                    viewModel = viewModel(key = "hackernews") { FeedViewModel("hackernews") },
                                    onOpenUrl = onOpenUrl,
                                    onOpenDigest = onOpenDigest
                                )
                                TrendingSource.ProductHunt -> FeedScreen(
                                    viewModel = viewModel(key = "producthunt") { FeedViewModel("producthunt") },
                                    onOpenUrl = onOpenUrl
                                )
                            }
                        }
                        HomeTab.Picks -> PicksScreen(
                            onNavigateToDetail = onNavigateToDetail,
                            onOpenUrl = onOpenUrl,
                            onNavigateToSubscribe = onNavigateToSubscribe,
                            modifier = contentModifier,
                            viewModel = viewModel { PicksViewModel() },
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
            }

            HomeFloatingBar(
                items = barItems,
                // 聊天未接入的平台（iOS）传 null，底栏不渲染 FAB、退化成纯胶囊
                onOpenChat = if (globalChatScreen != null) {
                    {
                        // 与 README 页的入口共用 chat_entry_click，把分散多处的进入路径收进
                        // 同一事件的 from 维度；home_fab 是 1.1 起的新值（此前 Chat 在胶囊里
                        // 当伪 tab，记的是 home_tab + tab_switch，前后可对比入口点击量）
                        trackEvent("chat_entry_click", mapOf("from" to "home_fab"))
                        onNavigateToChat()
                    }
                } else null,
                // 定高与内容底部留白同源（contentBottomPadding 也按它算），两者不能各算各的。
                // 高度加在调用处而非组件内部，与 Echo 在 MainActivity 的写法一致。
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = barBottomInset + FloatingBarBottomMargin)
                    .height(FloatingBarHeight),
            )
        }
    }
}
