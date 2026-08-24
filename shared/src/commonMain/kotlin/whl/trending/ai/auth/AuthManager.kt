package whl.trending.ai.auth

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed interface AuthState {
    data object LoggedOut : AuthState
    data object LoggingIn : AuthState
    data object LoggedIn : AuthState
}

/**
 * 登录失败的粗粒度归因，喂 [SignInHintHost] 的提示弹窗。
 * 登录面板内的失败不走这里（面板内联红字处理），只留「发生在别处、需要明确告知」的。
 */
enum class SignInFailureReason {
    USER_CANCELED, TIMEOUT, NETWORK, NO_BROWSER, CONFIG,

    /**
     * 刷新被 `invalid_refresh_token` 拒绝，本地会话已清。发生在任意页面的后台刷新里，
     * 不提示的话用户只会发现自己莫名未登录。
     */
    SESSION_EXPIRED,
    OTHER,
}

/**
 * 进程级登录失败事件总线。用一次性事件而非 authState——「失败」与「未登录」的稳态都是 LoggedOut。
 * 不挂 [AuthManager] 实例字段：配置变更会重建实例，OAuth 回调可能仍落旧实例、事件必丢。
 * replay=1 防「先失败、后组合」丢事件，收集侧处理后须调 [consume]。
 */
object SignInFailureBus {
    private val _events = MutableSharedFlow<SignInFailureReason>(replay = 1, extraBufferCapacity = 1)
    val events: Flow<SignInFailureReason> = _events.asSharedFlow()

    fun emit(reason: SignInFailureReason) {
        _events.tryEmit(reason)
    }

    fun consume() {
        _events.resetReplayCache()
    }
}

/**
 * 登录态抽象：shared/UI 只依赖本接口。
 * iOS 未接入前使用 NoopAuthManager（isSupported=false，UI 隐藏登录入口）。
 * 登录失败事件不经本接口，统一走 [SignInFailureBus]。
 */
interface AuthManager {
    val isSupported: Boolean
    val authState: StateFlow<AuthState>

    /**
     * 请求登录：发布到 [LoginSheetBus]，由 App 根部的 LoginSheetHost 弹登录面板。
     * @param source 登录入口标识（如 "home_avatar"），随 sign_in_* 埋点上报做入口归因
     */
    fun signIn(source: String)

    fun signOut()
    suspend fun getAccessToken(): String?

    /**
     * 带鉴权执行一次请求：拿 token 交给 [block]，**若因 401 失败则刷新后重试一次**。
     * 默认实现不重试，loginbase 实现覆盖；返回 null 表示无会话，调用方按未登录处理。
     */
    suspend fun <T> authorized(block: suspend (String) -> T): T? {
        val token = getAccessToken() ?: return null
        return block(token)
    }
}

object NoopAuthManager : AuthManager {
    override val isSupported: Boolean = false
    override val authState: StateFlow<AuthState> = MutableStateFlow(AuthState.LoggedOut)
    override fun signIn(source: String) {}
    override fun signOut() {}
    override suspend fun getAccessToken(): String? = null
}

/** 仿 globalChatScreen 的依赖反转：Android 在 MainActivity.onCreate 注入实现 */
var globalAuthManager: AuthManager = NoopAuthManager

