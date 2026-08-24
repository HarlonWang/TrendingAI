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
import whl.trending.ai.chat.globalChatScreen
import whl.trending.ai.core.App
import whl.trending.ai.core.ProCheckout
import whl.trending.ai.core.ProSponsor
import whl.trending.ai.core.ReconcileAction
import whl.trending.ai.core.analytics.AppEvent
import whl.trending.ai.core.analytics.CheckoutStepKind
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

    override fun onResume() {
        super.onResume()
        val loggedIn = whl.trending.ai.auth.globalAuthManager.authState.value is whl.trending.ai.auth.AuthState.LoggedIn
        if (!loggedIn) return

        // 绑定 GitHub 的回前台刷新窗口已随 loginbase link 流程删除——
        // 回跳带确定结果（?linked=github / ?error=），由 OAuthOutcomeHost 处理，
        // 不再需要「用户手动返回 + 30 分钟窗口内猜测」。

        // 赞助对账窗口
        if (!globalSettingsManager.currentIsPro() && ProSponsor.shouldReconcile()) {
            lifecycleScope.launch {
                val token = whl.trending.ai.auth.globalAuthManager.getAccessToken()
                val result = whl.trending.ai.data.repository.UserRepository().refreshPro(token)
                when (ProSponsor.reconcileAction(result)) {
                    ReconcileAction.MARK_PRO -> ProSponsor.markReconciled()

                    // 钱付了但没关联 GitHub：一并结束对账窗口，补对账交给关联流程——
                    // 不结束的话每次回前台都会再弹，比不提示还烦。
                    ReconcileAction.GUIDE_LINK -> {
                        ProSponsor.markReconciled()
                        ProSponsor.signalNeedsGithubLink()
                    }

                    // 窗口留着，下次回前台再试
                    ReconcileAction.STAY_SILENT -> Unit
                }
            }
        }

        // Paddle 购买对账窗口。与上面的赞助对账各查各的：这条走 /api/me（纯 D1 查询），
        // 不烧 pro/refresh 的 GitHub PAT 配额，因此可以重试多轮——权益要等 webhook 落库，
        // 用户完全可能比 webhook 先回到 App，查一次就放弃会把「已付款」显示成免费档。
        if (!globalSettingsManager.currentIsPro() && ProCheckout.shouldReconcile()) {
            lifecycleScope.launch {
                val repository = whl.trending.ai.data.repository.UserRepository()
                val attempt = ProCheckout.reconcile {
                    val token = whl.trending.ai.auth.globalAuthManager.getAccessToken()
                    // syncMe 会把 isPro 落到本地设置，UI 各处（模型选择器、配额卡）随之解锁
                    repository.syncMe(token) != null && globalSettingsManager.currentIsPro()
                }
                // attempt 区分「秒到」与「等满 26 秒」，是判断 webhook 时延是否需要调窗口的依据
                if (attempt != null) {
                    ProCheckout.markActivated()
                    track(AppEvent.CheckoutStep(CheckoutStepKind.RECONCILED, attempt = attempt))
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
