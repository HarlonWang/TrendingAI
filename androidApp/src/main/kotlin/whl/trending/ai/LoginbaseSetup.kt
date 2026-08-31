package whl.trending.ai

import android.app.Activity
import java.lang.ref.WeakReference
import wang.harlon.loginbase.OAuthProvider
import wang.harlon.loginbase.browser.link
import wang.harlon.loginbase.browser.signIn
import whl.trending.ai.auth.OAuthMode
import whl.trending.ai.auth.globalOAuthLauncher
import whl.trending.ai.auth.initLoginbaseAuth

private var activityRef: WeakReference<Activity>? = null

/**
 * loginbase 的全部 Android 接线，MainActivity.onCreate 调这一行即可，幂等：
 *
 * - 进程级一次：建 AuthClient/Manager（[initLoginbaseAuth] 幂等，重建安全）
 * - 每次 onCreate 刷新：OAuth 发起用的 Activity 弱引用（库的管理页从它启动）。
 *   browser 模块只在本模块出现，经 globalOAuthLauncher 注入给 shared（依赖反转，
 *   仿 globalChatScreen）
 */
internal fun installLoginbase(activity: Activity) {
    initLoginbaseAuth(activity.applicationContext) // 幂等，重复调用复用既有单例
    activityRef = WeakReference(activity)
    globalOAuthLauncher = { client, mode, clientFlowId ->
        val host = activityRef?.get()
        if (host == null) {
            false
        } else {
            when (mode) {
                OAuthMode.SIGN_IN -> client.signIn(host, OAuthProvider.GitHub, clientFlowId = clientFlowId)
                OAuthMode.LINK -> client.link(host, OAuthProvider.GitHub)
            }
            true
        }
    }
}
