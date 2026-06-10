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
        logtoClient.signOut { /* 本地凭证已清除即视为登出，远端失败不阻塞 */ }
        globalSettingsManager.setUserAvatarUrl(null)
        GithubTokenProvider.shared.clear()
        FollowingProvider.shared.clear()
        _authState.value = AuthState.LoggedOut
        trackEvent("sign_out")
    }

    override suspend fun getAccessToken(): String? =
        suspendCancellableCoroutine { cont ->
            logtoClient.getAccessToken { _, accessToken ->
                cont.resume(accessToken?.token)
            }
        }

    companion object {
        private const val LOGTO_APP_ID = "lasqslwwdjbim73vgkapj"

        /** release: cn.trendingai://whl.trending.ai/callback；debug 包名带 .debug，两条均已在 Logto 注册 */
        private val REDIRECT_URI = "cn.trendingai://${BuildConfig.APPLICATION_ID}/callback"
    }
}
