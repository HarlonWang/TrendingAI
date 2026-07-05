package whl.trending.ai.auth

import android.app.Activity
import io.logto.sdk.android.LogtoClient
import io.logto.sdk.android.exception.LogtoException
import io.logto.sdk.android.type.LogtoConfig
import java.io.IOException
import java.lang.ref.WeakReference
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    // extraBufferCapacity=1：即使此刻没有收集者（如提示宿主尚未组合），失败事件也不丢，
    // 待收集者上线后仍能拿到最近一次。
    private val _signInFailures = MutableSharedFlow<SignInFailureReason>(extraBufferCapacity = 1)
    override val signInFailures: Flow<SignInFailureReason> = _signInFailures.asSharedFlow()

    override fun signIn() {
        val activity = activityRef.get() ?: return
        _authState.value = AuthState.LoggingIn
        logtoClient.signIn(activity, REDIRECT_URI) { logtoException ->
            if (logtoException == null && logtoClient.isAuthenticated) {
                _authState.value = AuthState.LoggedIn
                trackEvent("sign_in_success")
            } else {
                _authState.value = AuthState.LoggedOut
                if (logtoException != null) {
                    val reason = classifySignInFailure(logtoException)
                    trackEvent("sign_in_failed", signInFailureProps(logtoException, reason))
                    _signInFailures.tryEmit(reason)
                }
            }
        }
    }

    /**
     * 把 Logto 登录异常归为粗粒度失败类别——单一事实来源，同时喂埋点 `reason` 与失败后的连通性提示。
     *
     * 注意：Logto SDK 构造多数 `LogtoException` 时不透传底层 `Throwable`（如授权码换 token 失败直接传
     * `null` cause），故「超时 vs 网络」通常只能落到同一 `NETWORK` 桶，只有 cause 恰好保留时才进一步区分为 `TIMEOUT`。
     */
    private fun classifySignInFailure(exception: LogtoException): SignInFailureReason {
        val logtoType = exception.message ?: LogtoException::class.java.simpleName
        val causeChain = generateSequence(exception.cause) { it.cause }.toList()
        return when {
            logtoType == LogtoException.Type.USER_CANCELED.name -> SignInFailureReason.USER_CANCELED
            causeChain.any { it is SocketTimeoutException } -> SignInFailureReason.TIMEOUT
            causeChain.any { it is UnknownHostException || it is ConnectException || it is IOException } -> SignInFailureReason.NETWORK
            logtoType in NETWORK_LOGTO_TYPES -> SignInFailureReason.NETWORK
            logtoType == LogtoException.Type.UNABLE_TO_LAUNCH_BROWSER.name -> SignInFailureReason.NO_BROWSER
            logtoType in CONFIG_LOGTO_TYPES -> SignInFailureReason.CONFIG
            else -> SignInFailureReason.OTHER
        }
    }

    /**
     * 登录失败埋点属性，用于登录漏斗排障：
     * - `reason`：粗粒度失败类别（见 [classifySignInFailure]），做漏斗看板；
     * - `logto_type`：Logto 原始异常类型名（`LogtoException.message` 即 `Type.name()`），保留细粒度；
     * - `cause`：底层异常类名，尽力而为。
     */
    private fun signInFailureProps(exception: LogtoException, reason: SignInFailureReason): Map<String, Any> {
        val logtoType = exception.message ?: LogtoException::class.java.simpleName
        val rootCause = generateSequence(exception.cause) { it.cause }.lastOrNull()
        return buildMap {
            put("reason", reason.name.lowercase())
            put("logto_type", logtoType)
            rootCause?.let { put("cause", it::class.java.simpleName) }
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

        /** 远端拉取失败——网络不可达 / 服务端异常，归为 `network`。 */
        private val NETWORK_LOGTO_TYPES = setOf(
            LogtoException.Type.UNABLE_TO_FETCH_OIDC_CONFIG.name,
            LogtoException.Type.UNABLE_TO_FETCH_TOKEN_BY_AUTHORIZATION_CODE.name,
            LogtoException.Type.UNABLE_TO_FETCH_USER_INFO.name,
            LogtoException.Type.UNABLE_TO_FETCH_JWKS_JSON.name,
        )

        /** 回跳 / 重定向配置错误，归为 `config`。 */
        private val CONFIG_LOGTO_TYPES = setOf(
            LogtoException.Type.INVALID_REDIRECT_URI.name,
            LogtoException.Type.INVALID_CALLBACK_URI.name,
        )

        /**
         * release: cn.trendingai://whl.trending.ai/callback；debug 包名带 .debug，两条均已在 Logto 注册。
         * 同时用作登录回跳与登出后回跳（post sign-out redirect URI）。scheme 与 manifestPlaceholder
         * `logtoRedirectScheme` 共用 BuildConfig.LOGTO_REDIRECT_SCHEME 单一来源（见 androidApp/build.gradle.kts）。
         */
        private val REDIRECT_URI = "${BuildConfig.LOGTO_REDIRECT_SCHEME}://${BuildConfig.APPLICATION_ID}/callback"
    }
}
