package whl.trending.ai.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import wang.harlon.loginbase.AuthClient
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
    /** OAuth 回跳 deepLink，如 `cn.trendingai://whl.trending.ai/auth`；须与服务端白名单匹配 */
    val redirectUri: String,
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
            val fresh = client.accessToken(forceRefresh = true) ?: return null
            block(fresh)
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
     * 登出的本地清理（与 [LogtoAuthManager.clearLocalUserState] 同集合）。
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
fun initLoginbaseAuth(tokenStore: TokenStore, redirectUri: String): LoginbaseAuthManager {
    val manager = LoginbaseAuthManager(AuthClient(AUTH_BASE_URL, tokenStore), redirectUri)
    globalAuthManager = manager
    return manager
}

/**
 * OAuth 回跳结果总线。系统浏览器完成 GitHub 授权后回跳 App，平台层（Android 的
 * MainActivity）把 deepLink 参数投递到这里，由登录面板/账户页消费。
 *
 * 用一次性事件而非状态：回跳是事件，处理完就没了；replay=1 让「回跳先到、收集者后
 * 组合」（进程被系统回收后从 deepLink 冷启动）的情况也拿得到。
 */
object OauthCallbackBus {
    private val _events = MutableSharedFlow<OauthCallback>(replay = 1, extraBufferCapacity = 1)
    val events: SharedFlow<OauthCallback> = _events.asSharedFlow()

    /**
     * 是否有已投递但尚未被消费的回跳。
     *
     * 给「用户关掉浏览器」的兜底判定用：回跳成功时 emit 与收集者处理之间隔着一次
     * 协程调度，而 ON_RESUME 可能插在中间——不看这个标记的话，会把成功的流程误判成
     * 取消、把 loading 复位掉（用户在那一瞬间能点到按钮）。
     */
    var hasPending: Boolean = false
        private set

    fun emit(callback: OauthCallback) {
        hasPending = true
        _events.tryEmit(callback)
    }

    fun consume() {
        hasPending = false
        _events.resetReplayCache()
    }

    /**
     * 解析回跳 URL 的 query。三种结局对应协议的三种回跳形态：
     * `?otc=` 登录成功、`?linked=github` 绑定成功、`?error=` 两者的失败。
     */
    fun parse(url: String): OauthCallback? {
        val query = url.substringAfter('?', "").takeIf { it.isNotEmpty() } ?: return null
        val params = query.split('&').mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null else it.substring(0, i) to decodeUrlComponent(it.substring(i + 1))
        }.toMap()
        return when {
            params["otc"] != null -> OauthCallback.SignedIn(params.getValue("otc"))
            params["linked"] != null -> OauthCallback.Linked
            params["error"] != null -> OauthCallback.Failed(params.getValue("error"))
            else -> null
        }
    }

    private fun decodeUrlComponent(raw: String): String {
        val bytes = mutableListOf<Byte>()
        var i = 0
        while (i < raw.length) {
            when {
                raw[i] == '%' && i + 2 < raw.length -> {
                    val hex = raw.substring(i + 1, i + 3).toIntOrNull(16)
                    if (hex == null) { bytes.add(raw[i].code.toByte()); i++ }
                    else { bytes.add(hex.toByte()); i += 3 }
                }
                raw[i] == '+' -> { bytes.add(' '.code.toByte()); i++ }
                else -> { raw[i].toString().encodeToByteArray().forEach { bytes.add(it) }; i++ }
            }
        }
        return bytes.toByteArray().decodeToString()
    }
}

sealed interface OauthCallback {
    /** 登录回跳：拿 otc 去换令牌 */
    data class SignedIn(val otc: String) : OauthCallback
    /** 绑定身份成功 */
    data object Linked : OauthCallback
    /** 失败，reason 为协议错误码或 App 自定义的绑定冲突原因 */
    data class Failed(val error: String) : OauthCallback
}
