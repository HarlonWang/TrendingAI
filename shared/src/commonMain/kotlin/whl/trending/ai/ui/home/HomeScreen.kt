package whl.trending.ai.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.hackernews_title
import trendingai.shared.generated.resources.icon_producthunt_dark
import trendingai.shared.generated.resources.icon_producthunt_light
import trendingai.shared.generated.resources.me_title
import trendingai.shared.generated.resources.producthunt_title
import whl.trending.ai.chat.globalChatScreen
import whl.trending.ai.core.DigestPage
import whl.trending.ai.core.analytics.trackScreenView
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.data.repository.ChatModelsProvider
import whl.trending.ai.ui.common.LocalContentBottomPadding
import whl.trending.ai.ui.common.LocalContentTopPadding
import whl.trending.ai.ui.common.TrendingScaffold
import whl.trending.ai.ui.common.TrendingTopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.IconButton
import trendingai.shared.generated.resources.hiring_entry
import whl.trending.ai.ui.feed.FeedScreen
import whl.trending.ai.ui.feed.FeedViewModel
import whl.trending.ai.ui.picks.PicksScreen
import whl.trending.ai.ui.picks.PicksViewModel
import whl.trending.ai.ui.profile.ProfileScreen
import whl.trending.ai.ui.trending.TrendingScreen
import whl.trending.ai.ui.trending.TrendingViewModel

/**
 * 首页骨架：底栏胶囊三项（首页 / Picks / 我的）+ 右侧独立的 AI 对话 FAB。
 *
 * 首页内含 GitHub / Hacker News / Product Hunt 三个子源，用 [TrendingSourceTabs] 切换——
 * 只点击、不横滑：三个源各自有下拉刷新与横向可滚内容，再叠一层横向手势会互相抢。
 *
 * tab / 子源的状态与切换逻辑都在 [HomeViewModel]；本函数只负责按状态摆布局。
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
    onOpenHiring: () -> Unit = {},
    onNavigateToGithubProfile: () -> Unit = {},
    onNavigateToSubscription: () -> Unit = {},
    onNavigateToFavorites: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
) {
    val homeViewModel: HomeViewModel = viewModel {
        // Nav3 的默认 entry decorator 已接 SavedStateRegistry，createSavedStateHandle 应当可用；
        // 万一宿主没接（自定义 decorator 等），回退空 handle——代价只是进程被杀后回默认 tab。
        val handle = runCatching { createSavedStateHandle() }.getOrElse { SavedStateHandle() }
        HomeViewModel(savedStateHandle = handle)
    }
    val selectedTab by homeViewModel.selectedTab.collectAsState()
    val selectedSource by homeViewModel.selectedSource.collectAsState()

    // 沉浸式浏览开关（设置 › 个性化，默认关）。初值同步读，避免已开启用户冷启动闪一帧关闭态
    val immersiveEnabled by globalSettingsManager.immersiveBrowsing.collectAsState(
        remember { globalSettingsManager.currentImmersiveBrowsing() }
    )

    // 系统返回键/手势在非 Home tab 上先降级回 Home，再退才交给路由出栈（Material 底栏规范）。
    // 与 NavDisplay 共用同一 NavigationEventDispatcher；本 handler 只在 Home entry 位于栈顶时
    // 在组合树里，二级页在顶时不会误拦。
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = selectedTab != HomeTab.Home,
        onBackCompleted = { homeViewModel.backToHome() },
    )

    // syncMe（建档 + 头像/GitHub 身份/isPro 缓存）不在此触发——已随收藏同步一起挂在 App 根部
    // （见 App.kt）：登录常发生在「我的」tab，Home 的 LaunchedEffect 彼时仍在，但登录也可能
    // 发生在被推到栈顶的子页，挂这里会漏掉那条路径（isPro 不回写，Pro 用户被当免费档）。

    // 预热聊天模型目录：让选择器 chip 在用户首次进入 chat 前就绪，避免冷首拉导致 chip 迟迟不出现。
    LaunchedEffect(Unit) {
        if (globalChatScreen != null) {
            ChatModelsProvider.warmUp(this)
        }
    }

    // 页面浏览埋点的 tab 源（路由源在 App.kt）。Home 路由是容器不自报，三个 tab 才是页面。
    // key 是 tab，故切 tab 报一条；从二级页返回时本 entry 重新组合、这里重跑，返回也计一次浏览。
    LaunchedEffect(selectedTab) {
        selectedTab.screen?.let(::trackScreenView)
    }

    TrendingScaffold(
        // 顶栏不占 Scaffold 的 topBar 槽位：与悬浮底栏不占 bottomBar 同理——头部要浮在
        // 铺满全高的内容之上（沉浸式地基，见 LocalContentTopPadding 的 KDoc），
        // 占了槽位内容就被顶下去，将来头部收起时内容没法原地全展开。
    ) { _ ->
        // 内容层不消费 Scaffold padding：顶部由各页按 LocalContentTopPadding 让出，
        // 底部由 LocalContentBottomPadding 让出，列表才能从头部/底栏下面穿过去。
        val barBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val contentBottomPadding = barBottomInset + FloatingBarHeight + FloatingBarBottomMargin * 2
        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        // 头部高度用 M3 规范定值（TopAppBar 容器 64dp + SecondaryTabRow 48dp）而非实测上报：
        // 它是内容 contentPadding 的输入，实测 onSizeChanged 回写会引入首帧跳动。
        val topBarBottom = statusBarTop + TopBarHeight

        // 沉浸式状态：开关关闭时为 null，下游四个挂点全部原样返回（零开销，见 HomeImmersive)。
        // 手势行程取所有栏中最长的行程（Home 的子 tab 行底缘），它 1:1 跟手、其余栏按行程
        // 比例减速——视差即由此来。
        val immersiveState = if (immersiveEnabled) {
            rememberImmersiveState(
                topBarBottom + SourceTabRowHeight,
                selectedTab, selectedSource,
            )
        } else null

        val barItems = homeTabSpecs.map { spec ->
            HomeBarItem(
                key = spec.tab,
                iconSelected = spec.iconSelected,
                iconUnselected = spec.iconUnselected,
                label = stringResource(spec.label),
                selected = selectedTab == spec.tab,
                onClick = { homeViewModel.selectTab(spec.tab) },
            )
        }

        Box(modifier = Modifier.fillMaxSize().immersiveNestedScroll(immersiveState)) {
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
                    // 各页（与各自顶栏）都就地 viewModel() 自取：同一 ViewModelStore 返回同一
                    // 实例，不会多创建；转场期间旧页仍在组合树里，也不依赖任何外部提升的引用。
                    //
                    // LocalContentTopPadding 必须 provide 在页级（此 lambda 内）：转场期间新旧
                    // 两页同时在树，Home 的头部比 Picks/我的多一条子 tab 行，provide 在外层
                    // 会让其中一页拿错高度。
                    val contentTopPadding =
                        topBarBottom + if (tab == HomeTab.Home) SourceTabRowHeight else 0.dp
                    CompositionLocalProvider(LocalContentTopPadding provides contentTopPadding) {
                        when (tab) {
                            // 子 tab 行是 Home 页内的 overlay 而非外层头部的一部分：留在
                            // AnimatedContent 里，切 tab 时随页面横滑（与改造前一致）。沉浸式
                            // 需要的「钻进顶栏底下」不要求两者同容器——外层顶栏画在整个内容层
                            // 之后，z-order 天然在子 tab 行之上；将来只需让两者共享同一手势进度。
                            HomeTab.Home -> Box(modifier = Modifier.fillMaxSize()) {
                                when (selectedSource) {
                                    TrendingSource.GitHub -> TrendingScreen(
                                        onNavigateToDetail = onNavigateToDetail,
                                        viewModel = viewModel { TrendingViewModel() }
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
                                // 悬浮在铺满全高的列表之上，底色显式补 background——在内容流里时
                                // 它是透明底、透出的正是这块颜色；悬浮后透明会透出滚动中的列表。
                                // 沉浸位移：行程 = 顶栏底缘 + 自身高（三栏中最长），与手势行程相等
                                // ——1:1 跟手，比顶栏快，从顶栏底下钻过（顶栏在外层、画在其上）。
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .immersiveExit(
                                            immersiveState,
                                            ImmersiveEdge.Top,
                                            travel = topBarBottom + SourceTabRowHeight,
                                        )
                                        .padding(top = topBarBottom)
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.background)
                                ) {
                                    TrendingSourceTabs(
                                        selected = selectedSource,
                                        onSelect = { homeViewModel.selectSource(it) },
                                    )
                                }
                            }
                            HomeTab.Picks -> PicksScreen(
                                onNavigateToDetail = onNavigateToDetail,
                                onOpenUrl = onOpenUrl,
                                onNavigateToSubscribe = onNavigateToSubscribe,
                                viewModel = viewModel { PicksViewModel() },
                                onOpenDigest = onOpenDigest
                            )
                            HomeTab.Me -> ProfileScreen(
                                onNavigateToGithubProfile = onNavigateToGithubProfile,
                                onNavigateToSubscription = onNavigateToSubscription,
                                onNavigateToFavorites = onNavigateToFavorites,
                            )
                            HomeTab.Chat -> Unit
                        }
                    }
                }
            }

            // 顶栏层：浮在内容之上、与悬浮底栏同构。画在内容层之后，z-order 盖住页内的
            // 子 tab 行——沉浸式的视差（子 tab 行钻进顶栏底下）靠的就是这层跨层绘制顺序。
            //
            // 触摸不穿透的依据：M3 TopAppBar/TabRow 外层的 Surface 自带空 pointerInput，
            // 会吞掉落在其区域内的触摸，列表滚到头部背后也点不到被遮的 item。若将来把
            // 头部换成非 Surface 容器，必须自行补上这层消费，否则点击会穿透。
            // 沉浸位移：行程 = 状态栏 + 容器高（短于子 tab 行的行程 → 速率较慢，视差由此来）
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .immersiveExit(immersiveState, ImmersiveEdge.Top, travel = topBarBottom)
                    .fillMaxWidth()
            ) {
                when (selectedTab) {
                    HomeTab.Home -> when (selectedSource) {
                        TrendingSource.GitHub -> TrendingTopBar(
                            onSettingsClick = onNavigateToSettings,
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
                                // 招聘月度专题入口。只挂在 HN 源上——内容本来就是 HN 的，
                                // 来源归属由动线本身交代；GitHub / PH 的标题栏不受影响
                                leadingActions = {
                                    IconButton(onClick = onOpenHiring) {
                                        Icon(
                                            imageVector = Icons.Outlined.WorkOutline,
                                            contentDescription = stringResource(Res.string.hiring_entry),
                                        )
                                    }
                                },
                                onSettingsClick = onNavigateToSettings,
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
                                onSettingsClick = onNavigateToSettings,
                            )
                        }
                    }
                    HomeTab.Picks -> PicksTopBar(
                        onSettingsClick = onNavigateToSettings,
                    )
                    HomeTab.Me -> TrendingTopAppBar(
                        title = {
                            Text(
                                text = stringResource(Res.string.me_title),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        },
                        actions = { SettingsAction(onClick = onNavigateToSettings) },
                    )
                    // 选中态永不为 Chat（点击即推聊天页），这里只是穷尽 when
                    HomeTab.Chat -> Unit
                }
            }

            // 状态栏 scrim：头部退场后内容会滚到状态栏图标底下，垫一层同色系渐变保可读。
            // alpha 走 graphicsLayer 随进度淡入（draw 阶段读，滚动零重组）；常驻态 alpha=0
            // 完全不可见，不破坏关闭态/常驻态的逐像素一致。无 pointerInput，不挡触摸。
            if (immersiveState != null) {
                val scrimColor = MaterialTheme.colorScheme.background
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(statusBarTop)
                        .graphicsLayer { alpha = immersiveState.progress }
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(scrimColor, scrimColor.copy(alpha = 0f)),
                            )
                        )
                )
            }

            HomeFloatingBar(
                items = barItems,
                // 聊天未接入的平台（iOS）传 null，底栏不渲染 FAB、退化成纯胶囊
                // Chat 是路由，screen_viewed 由路由源自动产生（from 自动是当前 tab），这里不埋点
                onOpenChat = if (globalChatScreen != null) onNavigateToChat else null,
                // 定高与内容底部留白同源（contentBottomPadding 也按它算），两者不能各算各的。
                // 高度加在调用处而非组件内部，与 Echo 在 MainActivity 的写法一致。
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    // 沉浸位移：行程 = 胶囊高 + 外边距 + 导航栏 inset（胶囊顶边正好推到屏幕底沿）
                    .immersiveExit(
                        immersiveState,
                        ImmersiveEdge.Bottom,
                        travel = FloatingBarHeight + FloatingBarBottomMargin + barBottomInset,
                    )
                    .padding(horizontal = 16.dp)
                    .padding(bottom = barBottomInset + FloatingBarBottomMargin)
                    .height(FloatingBarHeight),
            )
        }
    }
}

/** M3 TopAppBar 的容器高度（TopAppBarSmallTokens.ContainerHeight），不含状态栏 inset。 */
private val TopBarHeight = 64.dp

/** [TrendingSourceTabs] 的高度：SecondaryTabRow 纯文字 tab 的规范定高。 */
private val SourceTabRowHeight = 48.dp
