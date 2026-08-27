package whl.trending.ai.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import wang.harlon.eventbase.Eventbase
import wang.harlon.loginbase.AuthClient
import wang.harlon.loginbase.AuthState as LoginbaseState
import wang.harlon.loginbase.RefreshOutcome
import wang.harlon.loginbase.SignOutReason
import wang.harlon.loginbase.TokenStore
import whl.trending.ai.core.analytics.AppEvent
import whl.trending.ai.core.analytics.AuthAction
import whl.trending.ai.core.analytics.setAnalyticsUser
import whl.trending.ai.core.analytics.track
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.data.remote.clearAuthTokenCache
import whl.trending.ai.data.repository.globalFavoriteRepository

/** loginbase 服务端挂载点（`/auth` 前缀，与 api.trendingai.cn 同域） */
private const val AUTH_BASE_URL = "https://api.trendingai.cn/auth"

/**
 * loginbase 实现。[signIn] 不拉起外部流程，发布请求让根部 LoginSheetHost 弹登录面板。
 * `AuthClient` 的单飞刷新锁是实例字段，必须全进程一个实例——由 [initLoginbaseAuth] 保证，别在别处 new。
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
                // 挂在这里而不是只挂 signOut()：被动失效不走那条路，落盘的 user_id
                // 不清会一直带在之后的匿名事件上
                if (state is LoginbaseState.SignedOut) {
                    setAnalyticsUser(null)
                    clearAuthTokenCache()
                    // 会话被服务端终结（refresh 撞 invalid_refresh_token）：本地会话已清，
                    // 不提示的话用户只会发现自己莫名未登录。主动登出与「压根没会话」不提示
                    if (state.reason is SignOutReason.SessionEnded) {
                        SignInFailureBus.emit(SignInFailureReason.SESSION_EXPIRED)
                    }
                }
                _authState.value = when (state) {
                    // Unknown 只在 restore 之前出现，对 App 而言等同未登录
                    LoginbaseState.Unknown, is LoginbaseState.SignedOut -> AuthState.LoggedOut
                    // RefreshFailed 不是登出（库文档硬性要求）：多半只是弱网，当登出处理会把
                    // 漫游/地铁用户踢下线
                    LoginbaseState.SignedIn, is LoginbaseState.RefreshFailed -> AuthState.LoggedIn
                }
            }
        }
    }

    /** 请求登录：弹 App 内登录面板（邮箱输入 + GitHub 按钮同屏） */
    override fun signIn(source: String) {
        LoginSheetBus.request(source)
    }

    override fun signOut() {
        scope.launch {
            client.signOut() // 尽力而为：服务端失败也清本地
            clearLocalUserState()
            setAnalyticsUser(null)
            track(AppEvent.SignedOut)
        }
    }

    override suspend fun getAccessToken(): String? = client.accessToken()

    override suspend fun refreshAccessToken(): String? =
        when (val outcome = client.refresh()) {
            is RefreshOutcome.Success -> outcome.tokens.accessToken
            // SessionEnded 的提示由 authState 收集器发出（会话终结必然过那条路）；
            // Failed（弱网等暂时性）/ NoSession 不提示，会话可能好好的
            else -> null
        }

    /** 登出的本地清理。仅限用户主动登出：它会清收藏等用户数据，被动的会话失效不得复用。 */
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

/** GitHub 授权结束后，面板需要据此恢复 UI 的结果（成功不在此列——成功直接关面板） */
enum class GithubAuthResult { FAILED, CANCELED }

/**
 * 登录面板请求总线。用 StateFlow 而非事件流——「当前是否有待处理的登录请求」是状态，
 * 配置变更重建后收集者能立刻恢复。
 */
object LoginSheetBus {
    private val _request = MutableStateFlow<String?>(null)

    /** 当前待处理的登录来源（null = 无请求） */
    val request: StateFlow<String?> = _request

    /**
     * GitHub 授权的结果，由常驻的 [whl.trending.ai.ui.common.OAuthOutcomeHost] 写、面板读。
     * 面板不直接订阅 `client.oauthResults`：授权跳出 App 回来时面板可能已不存在，结果会没有消费者。
     */
    private val _githubResult = MutableStateFlow<GithubAuthResult?>(null)
    val githubResult: StateFlow<GithubAuthResult?> = _githubResult

    /**
     * 发起一次登录请求。**漏斗起点 `auth_started` 记在这里**而非面板 composition——
     * 面板的 `LaunchedEffect` 随 Activity 重建重跑，一次登录会记成两条 started。
     * 同一入口的重复请求直接忽略，避免虚高分母。
     */
    fun request(source: String) {
        if (_request.value == source) return
        // 上一轮遗留的失败若留着，面板一打开就顶着红字
        _githubResult.value = null
        _request.value = source
        // 开一条 flow 串起本次登录：回跳可能是冷启动，终态事件靠落盘的 flow_id 才接得回同一个漏斗
        track(
            AppEvent.AuthStarted(AuthAction.SIGN_IN, method = "sheet", source = source),
            Eventbase.startFlow(),
        )
    }

    fun reportGithubResult(result: GithubAuthResult) {
        _githubResult.value = result
    }

    fun clear() {
        _request.value = null
        _githubResult.value = null
    }
}

/**
 * 进程级初始化，Android 在 MainActivity.onCreate 调用。传入的 TokenStore 必须同步 commit 落盘
 * （服务端「丢回执救活」的配套硬要求）——别改用 App 那份异步 apply 的 `Settings`，进程被杀会丢刚轮换的令牌。
 */
fun initLoginbaseAuth(tokenStore: TokenStore): LoginbaseAuthManager {
    // 幂等：本函数会被 Activity 重建路径反复经过
    (globalAuthManager as? LoginbaseAuthManager)?.let { return it }
    val manager = LoginbaseAuthManager(AuthClient(AUTH_BASE_URL, tokenStore))
    globalAuthManager = manager
    return manager
}
