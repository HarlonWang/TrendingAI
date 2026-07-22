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
 * 登录失败的粗粒度归因，同时喂埋点（USER_CANCELED 上报 sign_in_canceled 事件，
 * 其余映射为 sign_in_error 的 reason）与失败后的连通性提示。分类逻辑见 androidApp 的 LogtoAuthManager。
 */
enum class SignInFailureReason {
    USER_CANCELED, TIMEOUT, NETWORK, NO_BROWSER, CONFIG,

    /** 设备时钟偏差超出 id_token 校验容差（Logto SDK 60s）：重试无用，提示修时间（见 SignInHintHost） */
    CLOCK_SKEW,
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
 * 登录态抽象：shared/UI 只依赖本接口，Logto SDK 只存在于 androidApp。
 * iOS 未接入前使用 NoopAuthManager（isSupported=false，UI 隐藏登录入口）。
 * 登录失败事件不经本接口，统一走 [SignInFailureBus]。
 */
interface AuthManager {
    val isSupported: Boolean
    val authState: StateFlow<AuthState>

    /** @param source 登录入口标识（如 "home_avatar"），随 sign_in_start/success/canceled/error 埋点上报做入口归因 */
    fun signIn(source: String)
    fun signOut()
    suspend fun getAccessToken(): String?
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

/** Logto 租户端点（自定义域名，与 api.trendingai.cn 同走 Cloudflare 边缘）：客户端 SDK 与 Account API 共用 */
const val LOGTO_ENDPOINT = "https://auth.trendingai.cn"
