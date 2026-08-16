package whl.trending.ai

import android.app.Activity
import java.lang.ref.WeakReference
import wang.harlon.loginbase.OAuthProvider
import wang.harlon.loginbase.browser.link
import wang.harlon.loginbase.browser.signIn
import whl.trending.ai.auth.OAuthMode
import whl.trending.ai.auth.globalOAuthLauncher

/**
 * [globalOAuthLauncher] 的 Android 实现（仿 globalChatScreen 的依赖反转）：
 * loginbase-kt-browser 只在本模块出现。WeakReference：别把 Activity 钉在静态里。
 */
internal object LoginbaseOAuthLauncher {

    private var activityRef: WeakReference<Activity>? = null

    /** MainActivity.onCreate 里调用；单 Activity 结构下它就是唯一宿主 */
    fun install(activity: Activity) {
        activityRef = WeakReference(activity)
        globalOAuthLauncher = { client, mode ->
            val host = activityRef?.get()
            if (host == null) {
                false
            } else {
                when (mode) {
                    OAuthMode.SIGN_IN -> client.signIn(host, OAuthProvider.GitHub)
                    OAuthMode.LINK -> client.link(host, OAuthProvider.GitHub)
                }
                true
            }
        }
    }
}
