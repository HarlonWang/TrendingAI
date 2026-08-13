package whl.trending.ai.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import wang.harlon.loginbase.AuthClient
import wang.harlon.loginbase.TokenStore
import whl.trending.ai.core.platform.trackEvent
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.data.repository.globalFavoriteRepository
import wang.harlon.loginbase.AuthState as LoginbaseState

/** loginbase 服务端挂载点（`/auth` 前缀，与 api.trendingai.cn 同域） */
private const val AUTH_BASE_URL = "https://api.trendingai.cn/auth"

/**
 * loginbase 实现，替代 [LogtoAuthManager]。
 *
 * 与 Logto 时代最大的结构差别：**登录 UI 在 App 内**（邮箱验证码全程原生，不再跳
 * 托管页），所以 [signIn] 不再"拉起外部流程"，而是发布一个请求让根部的
 * LoginSheetHost 弹登录面板。GitHub 授权仍需外部浏览器（OAuth 授权页不能内嵌）。
 *
 * 单例性：`AuthClient` 的单飞刷新锁是实例字段，必须全进程一个实例——由
 * [initLoginbaseAuth] 保证，别在别处 new。
 */
class LoginbaseAuthManager(
    val client: AuthClient,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) : AuthManager {

    override val isSupported: Boolean = true

    private val _authState = MutableStateFlow<AuthState>(AuthState.LoggedOut)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        scope.launch {
            client.restore()
            client.authState.collect { state ->
                _authState.value = when (state) {
                    // Unknown 只在 restore 之前出现，对 App 而言等同未登录
                    LoginbaseState.Unknown, LoginbaseState.SignedOut -> AuthState.LoggedOut
                    LoginbaseState.SignedIn -> AuthState.LoggedIn
                }
            }
        }
    }

    /** 请求登录：弹 App 内登录面板（邮箱输入 + GitHub 按钮同屏，不再有方式选择器） */
    override fun signIn(source: String) {
        LoginSheetBus.request(source)
    }

    /**
     * 指定方式登录。保留双参重载是为了兼容既有调用点；新版两种方式在同一个面板里，
     * 故这里与单参行为一致——method 仅作为面板的初始意图。
     */
    override fun signIn(source: String, method: SignInMethod) {
        LoginSheetBus.request(source)
    }

    override fun signOut() {
        scope.launch {
            client.signOut() // 尽力而为：服务端失败也清本地
            clearLocalUserState()
            trackEvent("sign_out")
        }
    }

    override suspend fun getAccessToken(): String? = client.accessToken()

    /**
     * 登出的本地清理（与 [LogtoAuthManager.clearLocalUserState] 同集合）。
     *
     * **注意**：升级过渡期的"静默登出"**不得**复用这条路径——它会清收藏同步状态，
     * 而 C 方案的硬要求是升级导致的未登录态不能清任何用户数据（见 plan.md 第 4 步）。
     */
    private fun clearLocalUserState() {
        globalSettingsManager.setUserAvatarUrl(null)
        globalSettingsManager.setGithubIdentity(null, null)
        globalSettingsManager.setUserEmail(null)
        globalSettingsManager.setIsPro(false)
        globalSettingsManager.followServerDefault()
        globalFavoriteRepository.onSignOut()
        GithubTokenProvider.shared.clear()
        FollowingProvider.shared.clear()
        OwnRepoEventsProvider.shared.clear()
    }
}

/**
 * 登录面板请求总线。同 [SignInChooserBus] 的根部宿主思路：6 个登录入口零改动，
 * 面板只实现一次。用 StateFlow 而非事件流——"当前是否有待处理的登录请求"是状态，
 * 配置变更重建后收集者能立刻恢复。
 */
object LoginSheetBus {
    private val _request = MutableStateFlow<String?>(null)

    /** 当前待处理的登录来源（null = 无请求） */
    val request: StateFlow<String?> = _request

    fun request(source: String) {
        _request.value = source
    }

    fun clear() {
        _request.value = null
    }
}

/**
 * 进程级初始化。Android 在 MainActivity.onCreate 调用，传入平台存储实现——
 * `SharedPreferencesTokenStore` 用同步 commit 落盘，这是与服务端"丢回执救活"
 * 配套的硬要求，别改用 App 自己那份 `Settings`（multiplatform-settings 默认走
 * 异步 apply，进程被杀会丢刚轮换的令牌）。
 */
fun initLoginbaseAuth(tokenStore: TokenStore): LoginbaseAuthManager {
    val manager = LoginbaseAuthManager(AuthClient(AUTH_BASE_URL, tokenStore))
    globalAuthManager = manager
    return manager
}
