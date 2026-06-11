package whl.trending.ai.auth

import android.app.Activity
import android.content.Context
import io.logto.sdk.android.LogtoClient
import io.logto.sdk.android.type.LogtoConfig
import java.lang.ref.WeakReference
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import whl.trending.ai.BuildConfig
import whl.trending.ai.core.platform.trackEvent
import whl.trending.ai.data.local.globalSettingsManager

/**
 * Logto 实现：OIDC PKCE 登录，token 存储/刷新由 SDK 托管。
 * scope 额外加 identities——Worker 经 userinfo 取 GitHub 数字 ID 建档。
 */
class LogtoAuthManager(activity: Activity) : AuthManager {
    private val activityRef = WeakReference(activity)

    private val logtoClient = LogtoClient(
        LogtoConfig(
            endpoint = LOGTO_ENDPOINT,
            appId = LOGTO_APP_ID,
            scopes = listOf("identities"),
            resources = null,
            usingPersistStorage = true,
        ),
        activity.application,
    )

    /**
     * 登录态的权威真相源（优先于 SDK 存储）。
     *
     * Logto SDK 存在「登出与在途刷新竞态」：signOut 同步清空凭证后，一个早已发出、
     * 此刻才回包的 token 刷新会在 verifyAndSaveTokenResponse 里无条件把凭证写回
     * SharedPreferences，使 [LogtoClient.isAuthenticated] 复活为 true——仅凭它判断登录态，
     * 会在下次 Activity 重建时出现「登出后又显示已登录」。该标记一旦置位即压过 SDK 存储。
     */
    private val prefs = activity.application
        .getSharedPreferences(AUTH_PREFS_NAME, Context.MODE_PRIVATE)
    private val signedOut: Boolean get() = prefs.getBoolean(KEY_SIGNED_OUT, false)

    private val _authState = MutableStateFlow<AuthState>(
        if (logtoClient.isAuthenticated && !signedOut) AuthState.LoggedIn else AuthState.LoggedOut
    )
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()
    override val isSupported: Boolean = true

    init {
        // 复活检测：标记已登出、但 SDK 存储里又冒出凭证（被在途刷新写回）→ 再清一次并远端撤销
        if (signedOut && logtoClient.isAuthenticated) {
            logtoClient.signOut { /* 远端撤销失败不阻塞，本地清除即可 */ }
        }
    }

    override fun signIn() {
        val activity = activityRef.get() ?: return
        _authState.value = AuthState.LoggingIn
        logtoClient.signIn(activity, REDIRECT_URI) { logtoException ->
            if (logtoException == null && logtoClient.isAuthenticated) {
                prefs.edit().remove(KEY_SIGNED_OUT).apply()
                _authState.value = AuthState.LoggedIn
                trackEvent("sign_in_success")
            } else {
                _authState.value = AuthState.LoggedOut
                if (logtoException != null) trackEvent("sign_in_failed")
            }
        }
    }

    override fun signOut() {
        // 先持久化登出标记：即便随后被在途刷新复活，凭证也不再被采信（见构造的复活检测与 getAccessToken 守卫）
        prefs.edit().putBoolean(KEY_SIGNED_OUT, true).apply()
        logtoClient.signOut { /* 本地凭证已清除即视为登出，远端失败不阻塞 */ }
        globalSettingsManager.setUserAvatarUrl(null)
        GithubTokenProvider.shared.clear()
        FollowingProvider.shared.clear()
        OwnRepoEventsProvider.shared.clear()
        _authState.value = AuthState.LoggedOut
        trackEvent("sign_out")
    }

    override suspend fun getAccessToken(): String? {
        // 以持久化登出标记 + SDK 状态为准（与构造时登录态判定一致），不依赖可能过期的 _authState 快照：
        // 多实例并存时旧实例的 _authState 可能仍为 LoggedIn，而 marker 已置位。
        // 登出态既不触发刷新（缩小竞态源），也不交出可能被在途刷新复活的 token。
        if (signedOut || !logtoClient.isAuthenticated) return null
        return suspendCancellableCoroutine { cont ->
            logtoClient.getAccessToken { _, accessToken ->
                cont.resume(accessToken?.token)
            }
        }
    }

    companion object {
        private const val LOGTO_APP_ID = "lasqslwwdjbim73vgkapj"
        private const val AUTH_PREFS_NAME = "logto_auth_manager"
        private const val KEY_SIGNED_OUT = "user_signed_out"

        /** release: cn.trendingai://whl.trending.ai/callback；debug 包名带 .debug，两条均已在 Logto 注册 */
        private val REDIRECT_URI = "cn.trendingai://${BuildConfig.APPLICATION_ID}/callback"
    }
}
