package whl.trending.ai.auth

import android.app.Activity
import android.content.Context
import java.lang.ref.WeakReference
import wang.harlon.loginbase.AuthClient
import wang.harlon.loginbase.OAuthProvider
import wang.harlon.loginbase.SharedPreferencesTokenStore
import wang.harlon.loginbase.browser.link
import wang.harlon.loginbase.browser.signIn

/**
 * Android 侧初始化入口：把 loginbase 的类型封在 shared 内，androidApp 只需给一个 Context。
 *
 * TokenStore 用库的 SharedPreferences 实现——它**同步 commit 落盘**，是与服务端
 * 「丢回执救活」配套的硬要求（救活有 1h/3 次护栏）。别改用 App 自己那份 `Settings`：
 * multiplatform-settings 默认走异步 apply，进程被杀会丢掉刚轮换到的令牌。
 *
 * OAuth 的 redirect 不再从这里传：`loginbase-kt-browser` 从 manifest 的 meta-data
 * 推导（与中转页 intent-filter 用同一个占位符，物理上不可能漂移）。
 */
fun initLoginbaseAuth(context: Context): LoginbaseAuthManager =
    initLoginbaseAuth(SharedPreferencesTokenStore(context.applicationContext))

/**
 * 发起 OAuth 需要一个前台 Activity（库的管理页从它启动）。MainActivity 在 onCreate 里
 * attach；单 Activity 结构下它就是唯一宿主。WeakReference：别把 Activity 钉在静态里。
 */
object LoginbaseAuthUi {
    private var activityRef: WeakReference<Activity>? = null

    fun attach(activity: Activity) {
        activityRef = WeakReference(activity)
    }

    internal fun activity(): Activity? = activityRef?.get()
}

internal actual fun launchGithubSignIn(client: AuthClient): Boolean {
    val activity = LoginbaseAuthUi.activity() ?: return false
    client.signIn(activity, OAuthProvider.GitHub)
    return true
}

internal actual fun launchGithubLink(client: AuthClient): Boolean {
    val activity = LoginbaseAuthUi.activity() ?: return false
    client.link(activity, OAuthProvider.GitHub)
    return true
}
