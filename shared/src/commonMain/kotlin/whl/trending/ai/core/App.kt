package whl.trending.ai.core

import whl.trending.ai.ui.detail.ReadmeScreen
import whl.trending.ai.ui.favorites.FavoriteListScreen
import whl.trending.ai.ui.feedback.FeedbackScreen
import whl.trending.ai.ui.home.HomeScreen
import whl.trending.ai.ui.profile.GithubUserListMode
import whl.trending.ai.ui.profile.GithubUserListScreen
import whl.trending.ai.ui.profile.ProfileScreen
import whl.trending.ai.ui.profile.RepoListScreen
import whl.trending.ai.ui.settings.SettingsScreen
import whl.trending.ai.ui.subscribe.SubscribeScreen
import whl.trending.ai.ui.theme.TrendingTheme
import whl.trending.ai.ui.webview.WebViewScreen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import whl.trending.ai.chat.ChatContext
import whl.trending.ai.chat.globalChatScreen
import whl.trending.ai.core.platform.isCloudflareProtectedUrl
import whl.trending.ai.core.platform.openInCustomTab
import whl.trending.ai.ui.common.WhatsNewHost

data object Home
data object Settings
data object Feedback
data object Subscribe
data class RepoDetail(val owner: String, val repo: String)
data class WebPage(val url: String, val title: String)
data object Favorites
data object Profile
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

    TrendingTheme {
        WhatsNewHost()
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.safePop() },
            entryProvider = { key ->
                when (key) {
                    is Home -> NavEntry(key) {
                        HomeScreen(
                            onNavigateToSettings = {
                                backStack.add(Settings)
                            },
                            onNavigateToDetail = { owner, repo ->
                                backStack.add(RepoDetail(owner, repo))
                            },
                            onNavigateToChat = {
                                backStack.add(Chat(null))
                            },
                            onOpenUrl = { url ->
                                if (isCloudflareProtectedUrl(url)) {
                                    openInCustomTab(url)
                                } else {
                                    backStack.add(WebPage(url, ""))
                                }
                            },
                            onNavigateToProfile = {
                                backStack.add(Profile)
                            },
                        )
                    }

                    is Settings -> NavEntry(key) {
                        SettingsScreen(
                            onBack = {
                                backStack.safePop()
                            },
                            onNavigateToFavorites = {
                                backStack.add(Favorites)
                            },
                            onNavigateToFeedback = {
                                backStack.add(Feedback)
                            },
                            onNavigateToSubscribe = {
                                backStack.add(Subscribe)
                            },
                            onNavigateToWebPage = { url, title ->
                                backStack.add(WebPage(url, title))
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
                                if (isCloudflareProtectedUrl(url)) {
                                    openInCustomTab(url)
                                } else {
                                    backStack.add(WebPage(url, ""))
                                }
                            }
                        )
                    }

                    is Profile -> NavEntry(key) {
                        ProfileScreen(
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
