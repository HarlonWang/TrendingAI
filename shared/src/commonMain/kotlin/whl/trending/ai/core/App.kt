package whl.trending.ai.core

import whl.trending.ai.core.analytics.Screen
import whl.trending.ai.core.analytics.TrackRouteScreenViews
import whl.trending.ai.ui.detail.ReadmeScreen
import whl.trending.ai.ui.favorites.FavoriteListScreen
import whl.trending.ai.ui.feedback.FeedbackScreen
import whl.trending.ai.ui.home.HomeScreen
import whl.trending.ai.ui.profile.GithubProfileScreen
import whl.trending.ai.ui.profile.GithubUserListMode
import whl.trending.ai.ui.profile.GithubUserListScreen
import whl.trending.ai.ui.profile.RepoListScreen
import whl.trending.ai.ui.settings.AboutScreen
import whl.trending.ai.ui.settings.AppearanceScreen
import whl.trending.ai.ui.settings.ColorLabScreen
import whl.trending.ai.ui.settings.DataSourcesScreen
import whl.trending.ai.ui.settings.SettingsScreen
import whl.trending.ai.ui.digest.DigestPage
import whl.trending.ai.ui.hiring.HiringScreen
import whl.trending.ai.ui.digest.DigestScreen
import whl.trending.ai.ui.subscribe.SubscribeScreen
import whl.trending.ai.ui.theme.TrendingTheme
import whl.trending.ai.ui.webview.WebViewScreen

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import whl.trending.ai.auth.AuthState
import whl.trending.ai.auth.globalAuthManager
import whl.trending.ai.data.repository.UserRepository
import whl.trending.ai.data.repository.globalFavoriteRepository
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import whl.trending.ai.chat.ChatContext
import whl.trending.ai.chat.globalChatScreen
import whl.trending.ai.core.platform.openUrl
import whl.trending.ai.ui.common.ForceUpdateGate
import whl.trending.ai.ui.common.SignInHintHost
import whl.trending.ai.ui.auth.LoginSheetHost
import whl.trending.ai.ui.subscription.SubscriptionScreen
import whl.trending.ai.ui.common.OAuthOutcomeHost
import whl.trending.ai.ui.common.CheckoutResultHost
import whl.trending.ai.ui.common.SponsorLinkHost
import whl.trending.ai.ui.common.WhatsNewHost

// 路由即页面身份：实现 [Route] 才能进 backStack，`screen` 是必填的——
// 新增页面在这里填一个枚举值，其余埋点代码一行都不用写（见 core/analytics/ScreenTracking.kt）。
// Home 是容器不是页面，故为 null，由 HomeScreen 按选中的 tab 自报。
data object Home : Route { override val screen: Screen? = null }
data object Appearance : Route { override val screen = Screen.APPEARANCE }
data object ColorLab : Route { override val screen = Screen.COLOR_LAB }
data object Settings : Route { override val screen = Screen.SETTINGS }
data object About : Route { override val screen = Screen.ABOUT }
data object DataSources : Route { override val screen = Screen.DATA_SOURCES }
data object Feedback : Route { override val screen = Screen.FEEDBACK }
data object Subscribe : Route { override val screen = Screen.SUBSCRIBE }
data object ProSubscription : Route { override val screen = Screen.PAYWALL }
data class RepoDetail(val owner: String, val repo: String) : Route {
    override val screen = Screen.README
}
data class WebPage(val url: String, val title: String) : Route {
    override val screen = Screen.WEB_PAGE
}
data object Favorites : Route { override val screen = Screen.FAVORITES }
data object GithubProfile : Route { override val screen = Screen.GITHUB_PROFILE }
data object ProfileFollowers : Route { override val screen = Screen.PROFILE_FOLLOWERS }
data object ProfileFollowing : Route { override val screen = Screen.PROFILE_FOLLOWING }
data object ProfileRepos : Route { override val screen = Screen.PROFILE_REPOS }
data class Chat(val context: ChatContext?) : Route { override val screen = Screen.CHAT }

/**
 * HN 招聘月度专题。[month] 为 'YYYY-MM'，null = 最新一期（默认入口不带月份）。
 *
 * 与 [whl.trending.ai.ui.digest.DigestPage] 不同，本路由**不兼任数据载体**：
 * 它不从列表带任何内容进来（专题内容整月一次拉取），因此按惯例声明在这里，
 * 而不是跟着页面住在 ui 包——DigestPage 住在别处是被迫的例外（见 Route 的 KDoc），
 * 不是可照抄的范式。
 */
data class Hiring(val month: String? = null) : Route { override val screen = Screen.HIRING }

/**
 * 安全出栈：栈底（Home）永不弹出。
 * 转场动画期间二次点击返回、快速连按系统返回都会触发多次出栈，
 * 无保护时会把栈弹空，下一帧重组 NavDisplay 抛
 * "IllegalArgumentException: NavDisplay backstack cannot be empty"。
 */
internal fun MutableList<Route>.safePop() {
    if (size > 1) removeAt(lastIndex)
}

@Composable
@Preview
fun App() {
    val backStack = remember { mutableStateListOf<Route>(Home) }

    // 收藏云同步 + 身份/Pro 态同步（syncMe）触发：放在 App 根部而非 HomeScreen——登录常发生在
    // 账户页（Home 已被覆盖、其 LaunchedEffect 已随 NavEntry 销毁），只挂 HomeScreen 会导致
    // 「账户页登录」这条主路径永远不触发。syncMe 是 setIsPro 的日常唯一写入点：曾只挂 Home，
    // 账户页重登后本地 isPro 恒 false、Pro 用户被当免费档提示升级（2026-08-03，会话失效重登
    // 成为常态路径后放大为必现）。根部 composition 全程存活，登录态一变即触发；收藏同步在
    // 仓库自有 scope 上跑（requestSync），不受任何屏切换取消。
    val authState by globalAuthManager.authState.collectAsState()
    val userRepository = remember { UserRepository() }
    LaunchedEffect(authState) {
        if (authState is AuthState.LoggedIn) {
            globalFavoriteRepository.requestSync()
            globalAuthManager.authorized { userRepository.syncMe(it) }
        }
    }

    // 外链统一出口：默认走系统浏览器（Custom Tabs / SFSafariViewController），
    // 用户在设置中关闭、或设备无浏览器可承接时，兜底进应用内 WebView。
    // GitHub README 阅读走 RepoDetail 路由，不经此处。
    val openExternalUrl: (url: String, title: String) -> Unit = { url, title ->
        openUrl(url, onInAppFallback = { backStack.add(WebPage(url, title)) })
    }

    // 覆盖 Compose 默认 UriHandler，让所有 openUri 调用与 Markdown 链接都收口到统一出口，
    // 无需逐处改造（Profile 列表、聊天回复链接等直接受益）。WebView 兜底无标题位，传空。
    val customUriHandler = remember(backStack) {
        object : UriHandler {
            override fun openUri(uri: String) = openExternalUrl(uri, "")
        }
    }

    TrendingTheme {
        // 转场兜底底色：NavDisplay 的 enter/exit 都带 fadeIn/fadeOut，那 200ms 内新旧页 alpha 都 <1，
        // 没有这层就会透出 Android window 背景。window 主题是 AppCompat.DayNight，跟的是系统深浅，
        // 而深色/AMOLED 是应用内 ThemeMode——系统浅色 + app 内深色时露出的是 #fafafa，整屏白闪。
        // 取 background 与各页 TrendingScaffold 的 containerColor 对齐；纯黑档不特判
        //（主题层的 isAmoled 已把 background 压到全黑）。
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            CompositionLocalProvider(LocalUriHandler provides customUriHandler) {
                ForceUpdateGate {
                    WhatsNewHost()
                    SignInHintHost()
                    LoginSheetHost()
                    SponsorLinkHost()
                    CheckoutResultHost()
                    OAuthOutcomeHost()
                    // 页面浏览埋点的路由源，全 app 就这一处；tab 源在 HomeScreen
                    TrackRouteScreenViews(backStack)
                    NavDisplay(
                        backStack = backStack,
                        onBack = { backStack.safePop() },
                        // 页面转场：新页沿前进方向滑入 1/8 屏、旧页往反方向滑出，各配 200ms 淡入淡出；
                        // 返回及预测式返回手势反向。位移量与时长取自 Echo 的 NavHost
                        // enter/exit/popEnter/popExitTransition——位移走默认 spring，只有淡化是 tween(200)。
                        // 首页切 tab 的 AnimatedContent 用的是同一组参数，全 app 一套转场语言。
                        transitionSpec = {
                            slideInHorizontally { it / 8 } + fadeIn(tween(200)) togetherWith
                                slideOutHorizontally { -it / 8 } + fadeOut(tween(200))
                        },
                        popTransitionSpec = {
                            slideInHorizontally { -it / 8 } + fadeIn(tween(200)) togetherWith
                                slideOutHorizontally { it / 8 } + fadeOut(tween(200))
                        },
                        // Nav3 特有：预测式返回手势。Echo 的 NavHost 没有对应物，与 pop 同参。
                        predictivePopTransitionSpec = {
                            slideInHorizontally { -it / 8 } + fadeIn(tween(200)) togetherWith
                                slideOutHorizontally { it / 8 } + fadeOut(tween(200))
                        },
                        entryProvider = { key ->
                            when (key) {
                            is Home -> NavEntry(key) {
                                HomeScreen(
                                    onNavigateToDetail = { owner, repo ->
                                        backStack.add(RepoDetail(owner, repo))
                                    },
                                    onNavigateToChat = {
                                        backStack.add(Chat(null))
                                    },
                                    onOpenUrl = { url ->
                                        openExternalUrl(url, "")
                                    },
                                    onNavigateToSubscribe = {
                                        backStack.add(Subscribe)
                                    },
                                    onOpenDigest = { page ->
                                        backStack.add(page)
                                    },
                                    onOpenHiring = {
                                        // 不带 month = 进最新一期
                                        backStack.add(Hiring())
                                    },
                                    onNavigateToGithubProfile = { backStack.add(GithubProfile) },
                                    onNavigateToSubscription = { backStack.add(ProSubscription) },
                                    onNavigateToFavorites = { backStack.add(Favorites) },
                                    onNavigateToSettings = { backStack.add(Settings) },
                                )
                            }

                            is Appearance -> NavEntry(key) {
                                AppearanceScreen(
                                    onBack = {
                                        backStack.safePop()
                                    },
                                    onNavigateToColorLab = {
                                        backStack.add(ColorLab)
                                    }
                                )
                            }

                            is ColorLab -> NavEntry(key) {
                                ColorLabScreen(
                                    onBack = {
                                        backStack.safePop()
                                    }
                                )
                            }

                            is About -> NavEntry(key) {
                                AboutScreen(
                                    onBack = {
                                        backStack.safePop()
                                    },
                                    onNavigateToWebPage = { url, title ->
                                        openExternalUrl(url, title)
                                    }
                                )
                            }

                            is DataSources -> NavEntry(key) {
                                DataSourcesScreen(onBack = { backStack.safePop() })
                            }

                            is Feedback -> NavEntry(key) {
                                FeedbackScreen(
                                    onBack = {
                                        backStack.safePop()
                                    }
                                )
                            }

                            is Subscribe -> NavEntry(key) {
                                SubscribeScreen(
                                    onBack = {
                                        backStack.safePop()
                                    }
                                )
                            }

                            is WebPage -> NavEntry(key) {
                                WebViewScreen(
                                    url = key.url,
                                    title = key.title,
                                    onBack = { backStack.safePop() }
                                )
                            }

                            is Favorites -> NavEntry(key) {
                                FavoriteListScreen(
                                    onBack = { backStack.safePop() },
                                    onNavigateToDetail = { owner, repo ->
                                        backStack.add(RepoDetail(owner, repo))
                                    },
                                    onOpenUrl = { url ->
                                        openExternalUrl(url, "")
                                    },
                                    onOpenDigest = { page ->
                                        backStack.add(page)
                                    }
                                )
                            }

                            is ProSubscription -> NavEntry(key) {
                                SubscriptionScreen(onBack = { backStack.safePop() })
                            }

                            is Settings -> NavEntry(key) {
                                SettingsScreen(
                                    onBack = { backStack.safePop() },
                                    onNavigateToAppearance = { backStack.add(Appearance) },
                                    onNavigateToSubscribe = { backStack.add(Subscribe) },
                                    onNavigateToFeedback = { backStack.add(Feedback) },
                                    onNavigateToAbout = { backStack.add(About) },
                                    onNavigateToDataSources = { backStack.add(DataSources) },
                                )
                            }

                            is GithubProfile -> NavEntry(key) {
                                GithubProfileScreen(
                                    onBack = { backStack.safePop() },
                                    onOpenFollowers = { backStack.add(ProfileFollowers) },
                                    onOpenFollowing = { backStack.add(ProfileFollowing) },
                                    onOpenRepos = { backStack.add(ProfileRepos) },
                                )
                            }

                            is ProfileFollowers -> NavEntry(key) {
                                GithubUserListScreen(
                                    mode = GithubUserListMode.FOLLOWERS,
                                    onBack = { backStack.safePop() },
                                )
                            }

                            is ProfileFollowing -> NavEntry(key) {
                                GithubUserListScreen(
                                    mode = GithubUserListMode.FOLLOWING,
                                    onBack = { backStack.safePop() },
                                )
                            }

                            is ProfileRepos -> NavEntry(key) {
                                RepoListScreen(
                                    onBack = { backStack.safePop() },
                                    onOpenRepo = { owner, repo ->
                                        backStack.add(RepoDetail(owner, repo))
                                    },
                                )
                            }

                            is RepoDetail -> NavEntry(key) {
                                ReadmeScreen(
                                    owner = key.owner,
                                    repo = key.repo,
                                    onBack = { backStack.safePop() },
                                    onNavigateToChat = { context ->
                                        backStack.add(Chat(context))
                                    }
                                )
                            }

                            is DigestPage -> NavEntry(key) {
                                DigestScreen(
                                    page = key,
                                    onBack = { backStack.safePop() },
                                    onOpenUrl = { url ->
                                        openExternalUrl(url, "")
                                    }
                                )
                            }

                            is Hiring -> NavEntry(key) {
                                HiringScreen(
                                    page = key,
                                    onBack = { backStack.safePop() },
                                    onOpenUrl = { url -> openExternalUrl(url, "") }
                                )
                            }

                            is Chat -> NavEntry(key) {
                                val screen = globalChatScreen
                                if (screen != null) {
                                    screen(key.context) { backStack.safePop() }
                                } else {
                                    // 未注册（如 iOS）——入口本应隐藏，兜底直接返回
                                    LaunchedEffect(Unit) { backStack.safePop() }
                                }
                            }

                            else -> {
                                error("Unknown route: $key")
                            }
                            }
                        }
                    )
                }
            }
        }
    }
}
