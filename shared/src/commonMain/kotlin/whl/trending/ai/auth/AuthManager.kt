package whl.trending.ai.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

sealed interface AuthState {
    data object LoggedOut : AuthState
    data object LoggingIn : AuthState
    data object LoggedIn : AuthState
}

/**
 * 登录态抽象：shared/UI 只依赖本接口，Logto SDK 只存在于 androidApp。
 * iOS 未接入前使用 NoopAuthManager（isSupported=false，UI 隐藏登录入口）。
 */
interface AuthManager {
    val isSupported: Boolean
    val authState: StateFlow<AuthState>
    fun signIn()
    fun signOut()
    suspend fun getAccessToken(): String?
}

object NoopAuthManager : AuthManager {
    override val isSupported: Boolean = false
    override val authState: StateFlow<AuthState> = MutableStateFlow(AuthState.LoggedOut)
    override fun signIn() {}
    override fun signOut() {}
    override suspend fun getAccessToken(): String? = null
}

/** 仿 globalChatScreen 的依赖反转：Android 在 MainActivity.onCreate 注入实现 */
var globalAuthManager: AuthManager = NoopAuthManager

/** Logto 租户端点（自定义域名，与 api.trendingai.cn 同走 Cloudflare 边缘）：客户端 SDK 与 Account API 共用 */
const val LOGTO_ENDPOINT = "https://auth.trendingai.cn"
