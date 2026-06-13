package whl.trending.ai.auth

import android.app.Activity
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
 *
 * v3 起登录/登出改走系统浏览器（Chrome Custom Tabs）。两点行为变化：
 * - 「登出与在途刷新竞态」已由 SDK 内置 CredentialGuard（乐观锁版本号）原生兜底，
 *   登出后回包的刷新不会再把凭证写回存储复活登录态，故不再需要自管登出标记。
 * - Custom Tabs 与系统浏览器共享会话 cookie：仅清本地凭证会让下次登录静默登回原账号、
 *   无法切换账号。故登出用浏览器版 [LogtoClient.signOut] 走 end session endpoint 结束服务端会话。
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

    private val _authState = MutableStateFlow<AuthState>(
        if (logtoClient.isAuthenticated) AuthState.LoggedIn else AuthState.LoggedOut
    )
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()
    override val isSupported: Boolean = true

    override fun signIn() {
        val activity = activityRef.get() ?: return
        _authState.value = AuthState.LoggingIn
        logtoClient.signIn(activity, REDIRECT_URI) { logtoException ->
            if (logtoException == null && logtoClient.isAuthenticated) {
                _authState.value = AuthState.LoggedIn
                trackEvent("sign_in_success")
            } else {
                _authState.value = AuthState.LoggedOut
                if (logtoException != null) trackEvent("sign_in_failed")
            }
        }
    }

    override fun signOut() {
        // 完整登出：SDK 先撤销 refresh token，再经系统浏览器结束 Logto 会话、回跳 app。
        // 本地凭证在 signOut 启动时即同步清除，故可立即置登出态；远端/浏览器步骤为尽力而为、失败不阻塞。
        // 无可用 Activity 时（如后台错误处理）退回本地登出。
        val activity = activityRef.get()
        if (activity != null) {
            logtoClient.signOut(activity, REDIRECT_URI) { /* 浏览器/撤销失败不阻塞本地登出 */ }
        } else {
            logtoClient.clearCredentials { /* 本地凭证已清除即视为登出 */ }
        }
        globalSettingsManager.setUserAvatarUrl(null)
        GithubTokenProvider.shared.clear()
        FollowingProvider.shared.clear()
        OwnRepoEventsProvider.shared.clear()
        _authState.value = AuthState.LoggedOut
        trackEvent("sign_out")
    }

    override suspend fun getAccessToken(): String? {
        // 竞态由 SDK 的 CredentialGuard 兜底：登出后的在途刷新会以 NOT_AUTHENTICATED 收尾、不写回。
        if (!logtoClient.isAuthenticated) return null
        return suspendCancellableCoroutine { cont ->
            logtoClient.getAccessToken { _, accessToken ->
                cont.resume(accessToken?.token)
            }
        }
    }

    companion object {
        private const val LOGTO_APP_ID = "lasqslwwdjbim73vgkapj"

        /**
         * release: cn.trendingai://whl.trending.ai/callback；debug 包名带 .debug，两条均已在 Logto 注册。
         * 同时用作登录回跳与登出后回跳（post sign-out redirect URI）；scheme 须与 manifestPlaceholder
         * `logtoRedirectScheme` 一致（见 androidApp/build.gradle.kts）。
         */
        private val REDIRECT_URI = "cn.trendingai://${BuildConfig.APPLICATION_ID}/callback"
    }
}
