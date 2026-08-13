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

    /**
     * 静默刷新被 Logto 以 invalid_grant 拒绝——refresh token 在服务端已不存在（吊销/到期/轮换竞态）。
     * 与登录流程失败不同：此时本地会话已被清除（见 LogtoAuthManager 的会话失效处理），
     * 提示的目的是让用户知道「需要重新登录」，而不是对着账户页的失败态反复重试。
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

/** 登录方式：GitHub 走 directSignIn 直达授权页；邮箱走托管页验证码（first_screen 直达邮箱输入屏） */
enum class SignInMethod { GITHUB, EMAIL }

/**
 * 登录方式选择请求总线：`signIn(source)` 不再直接拉起授权，而是发布一个「待选择」请求，
 * 由 App 根部的 SignInMethodChooserHost 弹底部选择器，选中后回调 `signIn(source, method)`。
 * 好处：6 个登录入口零改动，选择器 UI 只实现一次（同 [SignInFailureBus] 的根部宿主思路）。
 *
 * 用 StateFlow 而非 SharedFlow：选择器是「当前是否有待选请求」的状态而非事件流，
 * 配置变更重建后收集者能立刻恢复弹窗（replay 语义天然成立）。
 */
object SignInChooserBus {
    private val _request = MutableStateFlow<String?>(null)

    /** 当前待选择的登录来源（null = 无待选请求） */
    val request: StateFlow<String?> = _request

    fun request(source: String) {
        _request.value = source
    }

    fun clear() {
        _request.value = null
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

    /**
     * 请求登录：发布到 [SignInChooserBus]，待用户在根部选择器里选定方式后进入 [signIn] 双参重载。
     * @param source 登录入口标识（如 "home_avatar"），随 sign_in_* 埋点上报做入口归因
     */
    fun signIn(source: String)

    /** 以指定方式拉起授权流程（选择器回调；亦可跳过选择器直接指定方式） */
    fun signIn(source: String, method: SignInMethod)

    fun signOut()
    suspend fun getAccessToken(): String?

    /**
     * 带鉴权执行一次请求：拿 token 交给 [block]，**若因 401 失败则刷新后重试一次**。
     *
     * 为什么需要它：access token 是短命的（1 小时），过期后业务请求会 401，
     * 而调用方各自 `getAccessToken()` 后直接发请求——没有重试就直接把
     * 「加载失败」摆给用户看，实际上刷新一下就能继续（实测：账户页显示
     * "Couldn't load credits right now"，其实只是 token 过期）。
     *
     * 默认实现不重试（Logto SDK 自己管刷新）；loginbase 实现覆盖它。
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
    override fun signIn(source: String, method: SignInMethod) {}
    override fun signOut() {}
    override suspend fun getAccessToken(): String? = null
}

/** 仿 globalChatScreen 的依赖反转：Android 在 MainActivity.onCreate 注入实现 */
var globalAuthManager: AuthManager = NoopAuthManager

/** Logto 租户端点（自定义域名，与 api.trendingai.cn 同走 Cloudflare 边缘）：客户端 SDK 与 Account API 共用 */
const val LOGTO_ENDPOINT = "https://auth.trendingai.cn"
