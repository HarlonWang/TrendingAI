package whl.trending.ai.auth

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

sealed interface AuthState {
    data object LoggedOut : AuthState
    data object LoggingIn : AuthState
    data object LoggedIn : AuthState
}

/**
 * 登录失败的粗粒度归因，同时喂埋点（[whl.trending.ai.auth] 内映射为 sign_in_failed 的 reason）
 * 与失败后的连通性提示。分类逻辑见 androidApp 的 LogtoAuthManager。
 */
enum class SignInFailureReason {
    USER_CANCELED, TIMEOUT, NETWORK, NO_BROWSER, CONFIG, OTHER,
}

/**
 * 登录态抽象：shared/UI 只依赖本接口，Logto SDK 只存在于 androidApp。
 * iOS 未接入前使用 NoopAuthManager（isSupported=false，UI 隐藏登录入口）。
 */
interface AuthManager {
    val isSupported: Boolean
    val authState: StateFlow<AuthState>

    /**
     * 登录失败的一次性事件流：每次失败 emit 一个归因，供 UI 弹连通性提示。
     * 用一次性事件而非 authState，是因为「失败」与「未登录」的稳态都是 LoggedOut，无法区分。
     */
    val signInFailures: Flow<SignInFailureReason>

    fun signIn()
    fun signOut()
    suspend fun getAccessToken(): String?
}

object NoopAuthManager : AuthManager {
    override val isSupported: Boolean = false
    override val authState: StateFlow<AuthState> = MutableStateFlow(AuthState.LoggedOut)
    override val signInFailures: Flow<SignInFailureReason> = emptyFlow()
    override fun signIn() {}
    override fun signOut() {}
    override suspend fun getAccessToken(): String? = null
}

/** 仿 globalChatScreen 的依赖反转：Android 在 MainActivity.onCreate 注入实现 */
var globalAuthManager: AuthManager = NoopAuthManager

/** Logto 租户端点（自定义域名，与 api.trendingai.cn 同走 Cloudflare 边缘）：客户端 SDK 与 Account API 共用 */
const val LOGTO_ENDPOINT = "https://auth.trendingai.cn"
