package whl.trending.ai

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import whl.trending.ai.chat.globalChatScreen
import whl.trending.ai.core.App
import whl.trending.ai.core.ProSponsor
import whl.trending.ai.data.local.AppLanguage
import whl.trending.ai.data.local.ThemeMode
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.core.platform.trackEvent
import whl.trending.ai.ui.home.HomeTab
import whl.trending.ai.ui.home.HomeTabRequest
import whl.trending.chat.ui.ChatScreen
import whl.trending.notifier.AndroidDailyPicksNotifier
import whl.trending.notifier.EXTRA_OPEN_TAB
import whl.trending.notifier.TAB_PICKS

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        whl.trending.ai.core.platform.AndroidContextHolder.initialize(this)

        // 注入 Logto 登录实现（仿 globalChatScreen 的依赖反转；配置变更重建时重新绑定 activity）
        whl.trending.ai.auth.globalAuthManager = whl.trending.ai.auth.LogtoAuthManager(this)

        // 注入每日 Picks 本地通知实现（须在 onCreate：权限 launcher 要求 STARTED 前注册），
        // 并对账调度状态；处理通知点击带来的切 tab 深链
        whl.trending.ai.notification.globalDailyPicksNotifier = AndroidDailyPicksNotifier(this)
        AndroidDailyPicksNotifier.syncOnAppStart(applicationContext)
        handleOpenTabIntent(intent)

        // 注册 Android-only 的 ChatScreen 到 CMP 导航 slot（仿 UpdateWrapper 的依赖反转）
        globalChatScreen = { context, onBack ->
            ChatScreen(initialContext = context, onBack = onBack)
        }

        lifecycleScope.launch {
            globalSettingsManager.appLanguage
                .distinctUntilChanged()
                .collect { language ->
                    val localeList = when (language) {
                        AppLanguage.CHINESE -> LocaleListCompat.forLanguageTags("zh")
                        AppLanguage.ENGLISH -> LocaleListCompat.forLanguageTags("en")
                        AppLanguage.FOLLOW_SYSTEM -> LocaleListCompat.getEmptyLocaleList()
                    }
                    AppCompatDelegate.setApplicationLocales(localeList)
                }
        }

        setContent {
            val themeMode by globalSettingsManager.themeMode.collectAsState(ThemeMode.FOLLOW_SYSTEM)
            val isSystemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val isDark = when (themeMode) {
                ThemeMode.FOLLOW_SYSTEM -> isSystemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK, ThemeMode.AMOLED -> true
            }

            DisposableEffect(isDark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                    ) { isDark },
                    navigationBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                    ) { isDark },
                )
                onDispose {}
            }

            UpdateWrapper {
                App()
            }
        }
    }

    /**
     * 回到前台时对账 Pro：仅「已登录、非 Pro、且处于 ProSponsor 对账窗口内（刚打开过赞助页）」才查，
     * 让「浏览器完成赞助、返回 app」即时生效，不等 webhook。窗口外零请求——服务端
     * pro-refresh 每次都消耗 GitHub PAT 配额，不能拿 resume 当轮询点。
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOpenTabIntent(intent)
    }

    /** 通知点击深链：带 open_tab=picks 的 intent 切到 Picks tab（消费后清掉，防配置变更重放） */
    private fun handleOpenTabIntent(intent: Intent?) {
        if (intent?.getStringExtra(EXTRA_OPEN_TAB) == TAB_PICKS) {
            intent.removeExtra(EXTRA_OPEN_TAB)
            trackEvent("daily_picks_notification_open")
            HomeTabRequest.request(HomeTab.Picks)
        }
    }

    override fun onResume() {
        super.onResume()
        val loggedIn = whl.trending.ai.auth.globalAuthManager.authState.value is whl.trending.ai.auth.AuthState.LoggedIn
        if (loggedIn && !globalSettingsManager.currentIsPro() && ProSponsor.shouldReconcile()) {
            lifecycleScope.launch {
                val token = whl.trending.ai.auth.globalAuthManager.getAccessToken()
                if (whl.trending.ai.data.repository.UserRepository().refreshPro(token) == true) {
                    ProSponsor.markReconciled()
                }
            }
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
