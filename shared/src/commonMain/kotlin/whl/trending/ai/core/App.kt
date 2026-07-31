package whl.trending.ai.core

import whl.trending.ai.ui.detail.ReadmeScreen
import whl.trending.ai.ui.favorites.FavoriteListScreen
import whl.trending.ai.ui.feedback.FeedbackScreen
import whl.trending.ai.ui.home.HomeScreen
import whl.trending.ai.ui.profile.AccountScreen
import whl.trending.ai.ui.profile.GithubProfileScreen
import whl.trending.ai.ui.profile.GithubUserListMode
import whl.trending.ai.ui.profile.GithubUserListScreen
import whl.trending.ai.ui.profile.RepoListScreen
import whl.trending.ai.ui.settings.AboutScreen
import whl.trending.ai.ui.settings.AppSettingsScreen
import whl.trending.ai.ui.settings.AppearanceScreen
import whl.trending.ai.ui.settings.ColorLabScreen
import whl.trending.ai.ui.subscribe.SubscribeScreen
import whl.trending.ai.ui.theme.TrendingTheme
import whl.trending.ai.ui.webview.WebViewScreen

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import whl.trending.ai.auth.AuthState
import whl.trending.ai.auth.globalAuthManager
import whl.trending.ai.data.repository.globalFavoriteRepository
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
import whl.trending.ai.ui.common.SignInMethodChooserHost
import whl.trending.ai.ui.common.SponsorLinkHost
import whl.trending.ai.ui.common.WhatsNewHost

data object Home
data object Appearance
data object ColorLab
data object AppSettings
data object About
data object Feedback
data object Subscribe
data class RepoDetail(val owner: String, val repo: String)
data class WebPage(val url: String, val title: String)
data object Favorites
data object Profile
data object GithubProfile
data object ProfileFollowers
data object ProfileFollowing
data object ProfileRepos
data class Chat(val context: ChatContext?)

/**
 * 安全出栈：栈底（Home）永不弹出。
 * 转场动画期间二次点击返回、快速连按系统返回都会触发多次出栈，
 * 无保护时会把栈弹空，下一帧重组 NavDisplay 抛
 * "IllegalArgumentException: NavDisplay backstack cannot be empty"。
 */
internal fun MutableList<Any>.safePop() {
    if (size > 1) removeAt(lastIndex)
}

@Composable
@Preview
fun App() {
    val backStack = remember { mutableStateListOf<Any>(Home) }

    // 收藏云同步触发：放在 App 根部而非 HomeScreen——登录常发生在账户页（Home 已被覆盖、
    // 其 LaunchedEffect 已随 NavEntry 销毁），只挂 HomeScreen 会导致「账户页登录→进我的收藏」
    // 这条主路径永远不触发同步。根部 composition 全程存活，登录态一变即触发；实际同步在
    // 仓库自有 scope 上跑（requestSync），不受任何屏切换取消。
    val authState by globalAuthManager.authState.collectAsState()
    LaunchedEffect(authState) {
        if (authState is AuthState.LoggedIn) {
            globalFavoriteRepository.requestSync()
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
        CompositionLocalProvider(LocalUriHandler provides customUriHandler) {
            ForceUpdateGate {
                WhatsNewHost()
                SignInHintHost()
                SignInMethodChooserHost()
                SponsorLinkHost()
                NavDisplay(
                    backStack = backStack,
                    onBack = { backStack.safePop() },
                    // Material 水平共享轴转场（Nav3 官方推荐写法，全局统一）：
                    // 前进——新页从右滑入、旧页向左滑出；返回及预测式返回手势——反向。
                    transitionSpec = {
                        slideInHorizontally(initialOffsetX = { it }) togetherWith
                            slideOutHorizontally(targetOffsetX = { -it })
                    },
                    popTransitionSpec = {
                        slideInHorizontally(initialOffsetX = { -it }) togetherWith
                            slideOutHorizontally(targetOffsetX = { it })
                    },
                    predictivePopTransitionSpec = {
                        slideInHorizontally(initialOffsetX = { -it }) togetherWith
                            slideOutHorizontally(targetOffsetX = { it })
                    },
                    entryProvider = { key ->
                        when (key) {
                        is Home -> NavEntry(key) {
                            HomeScreen(
                                onNavigateToAccount = {
                                    backStack.add(Profile)
                                },
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
                                }
                            )
                        }

                        is Profile -> NavEntry(key) {
                            AccountScreen(
                                onBack = { backStack.safePop() },
                                onNavigateToGithubProfile = { backStack.add(GithubProfile) },
                                onNavigateToFavorites = { backStack.add(Favorites) },
                                onNavigateToFeedback = { backStack.add(Feedback) },
                                onNavigateToSubscribe = { backStack.add(Subscribe) },
                                onNavigateToAppearance = { backStack.add(Appearance) },
                                onNavigateToAppSettings = { backStack.add(AppSettings) },
                                onNavigateToAbout = { backStack.add(About) },
                            )
                        }

                        is AppSettings -> NavEntry(key) {
                            AppSettingsScreen(
                                onBack = { backStack.safePop() },
                                onNavigateToFeedback = { backStack.add(Feedback) },
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
