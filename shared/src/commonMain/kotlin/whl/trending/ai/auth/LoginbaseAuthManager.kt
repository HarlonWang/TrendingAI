package whl.trending.ai.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import wang.harlon.loginbase.AuthClient
import wang.harlon.loginbase.RefreshOutcome
import wang.harlon.loginbase.TokenStore
import whl.trending.ai.core.UpgradeNotice
import whl.trending.ai.core.platform.trackEvent
import whl.trending.ai.data.remote.ApiException
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.data.repository.globalFavoriteRepository
import wang.harlon.loginbase.AuthState as LoginbaseState

/** loginbase 服务端挂载点（`/auth` 前缀，与 api.trendingai.cn 同域） */
private const val AUTH_BASE_URL = "https://api.trendingai.cn/auth"

// 找 401 时沿 cause 链回溯的深度上限——自引用的 cause 会把回溯变成死循环
private const val MAX_CAUSE_DEPTH = 8

/**
 * loginbase 实现（2026-08 替代已删除的 LogtoAuthManager）。
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
                    LoginbaseState.Unknown, is LoginbaseState.SignedOut -> AuthState.LoggedOut
                    // RefreshFailed 不是登出：会话没被清、多半只是弱网（库文档的硬性要求，
                    // 当登出处理会把漫游/地铁用户踢下线）。被动失效的用户提示由
                    // [authorized] 的 SessionEnded 分支负责，这里不重复
                    LoginbaseState.SignedIn, is LoginbaseState.RefreshFailed -> AuthState.LoggedIn
                }
            }
        }
    }

    /** 请求登录：弹 App 内登录面板（邮箱输入 + GitHub 按钮同屏，不再有方式选择器） */
    override fun signIn(source: String) {
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
     * 401 → 单飞刷新 → 重试一次。只重试一次：刷新后仍 401 说明不是过期问题
     * （会话已被撤销、或该端点本就拒绝这个身份），再试无益。
     *
     * 刷新走 [AuthClient.refresh] 的单飞路径——并发的多个业务请求同时 401 时，
     * 只会产生一次真实刷新，不会打爆服务端的救活配额。
     *
     * **调用契约：401 必须以异常形式冒出来**，本方法才看得见。沿 `cause` 链找
     * [ApiException]，所以中间包一层别的异常类型也没关系，只要底层原因还在。
     *
     * **已知不覆盖的一类**：返回 `Boolean` 的写接口（`putFavorite` /
     * `deleteFavorite` / `batchPutFavorites`）把 401 变成 `false` 而不抛异常，
     * 这里无从感知。它们不会因此丢数据——失败的 op 留在待推队列里，下次 sync
     * 重试（见 FavoriteRepository.flushPending）；代价只是这一次上行同步延后。
     * 要让它们也参与重试，得先把那几个接口改成抛异常，属独立改动。
     */
    override suspend fun <T> authorized(block: suspend (String) -> T): T? {
        val token = client.accessToken() ?: return null
        return try {
            block(token)
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (!e.isUnauthorized()) throw e
            when (val outcome = client.refresh()) {
                is RefreshOutcome.Success -> block(outcome.tokens.accessToken)
                // 服务端明确说这个 refresh token 不存在了（吊销/重用检测/护栏）——本地会话
                // 已被清除。**必须告诉用户**：否则他只会发现自己莫名未登录，而这发生在
                // 任意页面（后台刷新），他根本没在看登录面板，没有别的地方能给出解释。
                is RefreshOutcome.SessionEnded -> {
                    SignInFailureBus.emit(SignInFailureReason.SESSION_EXPIRED)
                    null
                }
                // Failed（网络等暂时性）/ NoSession：会话可能好好的，不提示、不打扰
                else -> null
            }
        }
    }

    /** 异常本身或其 `cause` 链上是否有 401 的 [ApiException]。 */
    private fun Throwable.isUnauthorized(): Boolean {
        var current: Throwable? = this
        var depth = 0
        // 深度封顶：防自引用的 cause 链把这里转成死循环
        while (current != null && depth < MAX_CAUSE_DEPTH) {
            if (current is ApiException && current.statusCode == 401) return true
            current = current.cause
            depth++
        }
        return false
    }

    /**
     * 登出的本地清理（沿用 Logto 时代同一套清理集合）。
     *
     * **注意**：升级过渡期的"静默登出"**不得**复用这条路径——它会清收藏同步状态，
     * 而 C 方案的硬要求是升级导致的未登录态不能清任何用户数据（见 plan.md 第 4 步）。
     */
    private fun clearLocalUserState() {
        // 主动登出的人不该看到「账号系统已升级」——那条是给「升级后发现自己莫名
        // 未登录」的用户的。不标记的话，Logto 遗留文件还在、痕迹判定仍为真，
        // 每个登出用户都会在首页被提示一次。
        UpgradeNotice.markShown()
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
 * 登录面板请求总线：6 个登录入口零改动，
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
    // 幂等：已初始化则复用。AuthClient 的单飞锁是实例字段、每进程必须一个实例，
    // 而本函数会被 Activity 重建路径反复经过
    (globalAuthManager as? LoginbaseAuthManager)?.let { return it }
    val manager = LoginbaseAuthManager(AuthClient(AUTH_BASE_URL, tokenStore))
    globalAuthManager = manager
    return manager
}
