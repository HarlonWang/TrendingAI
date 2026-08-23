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
 *
 * 登录**面板内**的失败（错误码、OAuth 回跳失败）不走这里——那些用户正看着面板，
 * 内联红字比弹窗合适。这里只留「发生在别处、用户需要被明确告知」的那类。
 */
enum class SignInFailureReason {
    USER_CANCELED, TIMEOUT, NETWORK, NO_BROWSER, CONFIG,

    /**
     * 刷新被服务端以 `invalid_refresh_token` 拒绝——refresh token 已不存在
     * （吊销 / 重用检测 / 救活护栏）。此时本地会话已被清除，且这发生在**任意页面**
     * 的后台刷新里，用户没在看登录面板：不提示的话他只会发现自己莫名未登录。
     */
    SESSION_EXPIRED,
    OTHER,
}

/**
 * 进程级登录失败事件总线：每次失败 emit 一个归因，供 UI 弹连通性提示。
 * 用一次性事件而非 authState，是因为「失败」与「未登录」的稳态都是 LoggedOut，无法区分。
 *
 * 不挂在 [AuthManager] 实例字段上：配置变更（旋转/深色切换）会重建 Activity 并替换
 * [globalAuthManager] 实例，而 OAuth 回调可能仍落在旧实例——实例级流上的事件必然丢失。
 * replay=1 让「先失败、后组合」的收集者也能拿到最近一次；收集侧处理后调 [consume]
 * 清掉重放缓存，避免之后重建的收集者把旧失败再弹一遍。
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
     * 请求登录：发布到 [LoginSheetBus]，由 App 根部的 LoginSheetHost 弹登录面板
     * （邮箱验证码 + GitHub 同屏）。6 个登录入口零改动，面板只实现一次。
     * @param source 登录入口标识（如 "home_avatar"），随 sign_in_* 埋点上报做入口归因
     */
    fun signIn(source: String)

    fun signOut()
    suspend fun getAccessToken(): String?

    /**
     * 带鉴权执行一次请求：拿 token 交给 [block]，**若因 401 失败则刷新后重试一次**。
     *
     * 为什么需要它：access token 是短命的，过期后业务请求会 401，而调用方各自
     * `getAccessToken()` 后直接发请求——没有重试就直接把「加载失败」摆给用户看，
     * 实际上刷新一下就能继续。
     *
     * 默认实现不重试；loginbase 实现覆盖它。
     * 返回 null 表示无会话——调用方按未登录处理，与 [getAccessToken] 一致。
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

