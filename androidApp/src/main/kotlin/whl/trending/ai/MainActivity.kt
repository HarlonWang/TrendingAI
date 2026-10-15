package whl.trending.ai

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import whl.trending.ai.core.App
import whl.trending.ai.core.analytics.AppEvent
import whl.trending.ai.core.analytics.NotificationKind
import whl.trending.ai.core.analytics.track
import whl.trending.ai.ui.home.HomeTab
import whl.trending.ai.ui.home.HomeTabRequest
import wang.harlon.chatbase.ui.ChatScreen
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

        setContent {
            // 系统栏深浅由 appsettings-kmp 的 AppearanceTheme 按外观模式同步（App 根上）；
            // 界面语言的 per-app locale 同样由库在根上应用（要求本 Activity 是 AppCompatActivity），这里都不再重抄
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
