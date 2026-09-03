package whl.trending.ai

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import whl.trending.ai.core.App
import whl.trending.ai.core.analytics.AppEvent
import whl.trending.ai.core.analytics.NotificationKind
import whl.trending.ai.core.analytics.track
import whl.trending.ai.data.local.AppLanguage
import whl.trending.ai.data.local.ThemeMode
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.ui.home.HomeTab
import whl.trending.ai.ui.home.HomeTabRequest
import whl.trending.chat.ui.ChatScreen
import whl.trending.notifier.AndroidDailyPicksNotifier
import whl.trending.notifier.EXTRA_OPEN_TAB
import whl.trending.notifier.TAB_PICKS
import whl.trending.notifier.consumeDailyPicksNotificationOpen

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // AndroidContextHolder 已在 TrendingApplication.onCreate 初始化——必须早于埋点配置读
        // getAppVersion()，别在这里重复调用（两处初始化正是版本号那次事故后收敛掉的）

        // loginbase 全部接线（建单例 + OAuth 发起注入），幂等，重建安全——见其文档
        installLoginbase(this)

        // 注入每日 Picks 本地通知实现（须在 onCreate：权限 launcher 要求 STARTED 前注册），
        // 并对账调度状态；处理通知点击带来的切 tab 深链
        whl.trending.ai.notification.globalDailyPicksNotifier = AndroidDailyPicksNotifier(this)
        AndroidDailyPicksNotifier.syncOnAppStart(applicationContext)
        handleOpenTabIntent(intent)

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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOpenTabIntent(intent)
    }

    /**
     * 通知点击深链：带 open_tab=picks 的 intent 切到 Picks tab（消费后清掉，防配置变更重放）。
     * open 埋点另按通知携带的日期去重——最近任务重建会重投原始 intent，removeExtra 挡不住，
     * 8 月数据里 open 因此有重放虚高。
     */
    private fun handleOpenTabIntent(intent: Intent?) {
        if (intent?.getStringExtra(EXTRA_OPEN_TAB) == TAB_PICKS) {
            intent.removeExtra(EXTRA_OPEN_TAB)
            if (consumeDailyPicksNotificationOpen(applicationContext, intent)) {
                track(AppEvent.NotificationOpened(NotificationKind.DAILY_PICKS))
            }
            HomeTabRequest.request(HomeTab.Picks)
        }
    }

}

@Preview
@Composable
private fun AppAndroidPreview() {
    App()
}
