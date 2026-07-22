package whl.trending.ai.auth

import android.app.Activity
import android.os.SystemClock
import android.util.Log
import io.logto.sdk.android.LogtoClient
import io.logto.sdk.android.exception.LogtoException
import io.logto.sdk.android.type.LogtoConfig
import io.logto.sdk.android.type.SignInOptions
import io.logto.sdk.core.constant.DirectSignInMethod
import io.logto.sdk.core.type.DirectSignInOptions
import java.io.IOException
import java.lang.ref.WeakReference
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.coroutines.resume
import org.jose4j.jwt.consumer.ErrorCodes
import org.jose4j.jwt.consumer.InvalidJwtException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import whl.trending.ai.BuildConfig
import whl.trending.ai.core.platform.trackEvent
import whl.trending.ai.data.local.globalSettingsManager

/**
 * Logto 实现：OIDC PKCE 登录，token 存储/刷新由 SDK 托管。
 * scope 额外加 identities——Worker 经 userinfo 取 GitHub 数字 ID 建档。
 *
 * v3 起登录/登出改走系统浏览器（Chrome Custom Tabs）。两点行为变化：
 * - 「登出与在途刷新竞态」已由 SDK 内置 CredentialGuard（乐观锁版本号）原生兜底，
 *   登出后回包的刷新不会再把凭证写回存储复活登录态，故不再需要自管登出标记。
 * - Custom Tabs 与系统浏览器共享会话 cookie：仅清本地凭证会让下次登录静默登回原账号、
 *   无法切换账号。故登出用浏览器版 [LogtoClient.signOut] 走 end session endpoint 结束服务端会话。
 */
class LogtoAuthManager(activity: Activity) : AuthManager {
    private val activityRef = WeakReference(activity)

    /** 时钟偏差提示的进程级一次性标志（刷新路径专用；显式登录失败不受限，每次都给反馈） */
    private val clockSkewHinted = AtomicBoolean(false)

    private val logtoClient = LogtoClient(
        LogtoConfig(
            endpoint = LOGTO_ENDPOINT,
            appId = LOGTO_APP_ID,
            // email：邮箱验证码登录的 email claim；对存量会话不追溯（老 token 无此授权，重登后才有）
            scopes = listOf("identities", "email"),
            resources = null,
            usingPersistStorage = true,
        ),
        activity.application,
    )

    private val _authState = MutableStateFlow<AuthState>(
        if (logtoClient.isAuthenticated) AuthState.LoggedIn else AuthState.LoggedOut
    )
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()
    override val isSupported: Boolean = true

    override fun signIn(source: String) {
        // 不直接拉起授权：发布待选请求，App 根部 SignInMethodChooserHost 弹选择器后回调双参重载。
        // 不在此置 LoggingIn / 上报 start——选择器被划掉不产生任何漏斗事件，start 仍指「浏览器流程已拉起」。
        SignInChooserBus.request(source)
    }

    override fun signIn(source: String, method: SignInMethod) {
        val activity = activityRef.get() ?: return
        _authState.value = AuthState.LoggingIn
        // 漏斗起点事件：start 数减去 success/canceled/error 终态数 = 无终态的静默流失
        // （典型如用户停在授权页直接杀进程，SDK 回调永远不会到达）。
        val methodProp = method.name.lowercase()
        trackEvent("sign_in_start", mapOf("source" to source, "method" to methodProp))
        // 可达性探测（异步、不阻塞）：托管页在 Custom Tab 里加载失败时 SDK 收不到回调、只会表现为
        // 用户取消——auth_probe 把这个观测盲区显性化，是「继续 Logto vs 自研」决策的直接判据。
        trackAuthProbe(methodProp)
        // 登录耗时起点：单调时钟，不受系统时间/时区调整影响；source 同理用局部变量随闭包捕获，
        // 配置变更重建 Activity 后回调即便落在旧实例也仍读到本次登录的起点（见 SignInFailureBus）。
        val signInStartedAt = SystemClock.elapsedRealtime()
        val signInOptions = when (method) {
            // directSignIn：授权端点直接 302 到 GitHub，跳过 Logto 托管登录页（海外 Cloudflare 边缘
            // 无境内节点，SPA ~490KB、大陆单请求 ~1s，是登录取消漏斗的主要嫌疑）。target 需与
            // Logto 控制台 GitHub connector 的 target 一致；不匹配时 Logto 回落到普通登录页，不会报错。
            SignInMethod.GITHUB -> SignInOptions(
                redirectUri = REDIRECT_URI,
                directSignIn = DirectSignInOptions(method = DirectSignInMethod.SOCIAL, target = "github"),
            )
            // 邮箱验证码：无 directSignIn 等价物，必须经托管页；first_screen + identifier 让托管页
            // 直接落在邮箱输入屏，跳过页内的方式选择步骤。
            SignInMethod.EMAIL -> SignInOptions(
                redirectUri = REDIRECT_URI,
                firstScreen = FIRST_SCREEN_IDENTIFIER_SIGN_IN,
                identifiers = listOf(IDENTIFIER_EMAIL),
            )
        }
        logtoClient.signIn(activity, signInOptions) { logtoException ->
            // 从登录进入到本次回调的耗时：成功=登录总时长，失败=到失败的时长（取消即用户在授权页停留时长）。
            // 需结合事件/reason 解读：config/no_browser 等快失败耗时接近 0 属正常。
            val durationMs = SystemClock.elapsedRealtime() - signInStartedAt
            if (logtoException == null && logtoClient.isAuthenticated) {
                _authState.value = AuthState.LoggedIn
                // method 为选择器意图；托管页内切换方式的边角（邮箱屏仍可能展示社交按钮）不在客户端识别
                trackEvent(
                    "sign_in_success",
                    mapOf(
                        "source" to source,
                        "method" to methodProp,
                        "duration_ms" to durationMs,
                        "duration_bucket" to durationBucket(durationMs),
                    ),
                )
            } else {
                _authState.value = AuthState.LoggedOut
                if (logtoException != null) {
                    val (reason, props) = analyzeSignInFailure(logtoException)
                    // 「取消 vs 异常」拆到事件名层面：Aptabase 面板以事件名为中心，混在一个事件里
                    // 靠 reason 属性区分时，日常巡检无法一眼分辨产品漏斗问题与技术故障。
                    if (reason == SignInFailureReason.USER_CANCELED) {
                        trackEvent(
                            "sign_in_canceled",
                            mapOf(
                                "source" to source,
                                "method" to methodProp,
                                "duration_ms" to durationMs,
                                "duration_bucket" to durationBucket(durationMs),
                                "cancel_kind" to if (durationMs < CANCEL_KIND_QUICK_THRESHOLD_MS) "quick" else "wait",
                            ),
                        )
                    } else {
                        trackSignInError(props + ("source" to source) + ("method" to methodProp) + ("duration_ms" to durationMs))
                    }
                    if (BuildConfig.DEBUG) {
                        Log.w(TAG, "sign_in failed: reason=$reason props=$props", logtoException)
                    }
                    // 走进程级总线而非实例字段：配置变更重建 Activity 后回调可能落在旧实例上（见 SignInFailureBus）
                    SignInFailureBus.emit(reason)
                }
            }
        }
    }

    /**
     * 把 Logto 登录异常归为粗粒度失败类别，并生成 sign_in_error 埋点属性——logtoType 与 cause 链
     * 只推导一次，归因与埋点保证同源。USER_CANCELED 不上报本属性集，单独走 sign_in_canceled 事件。
     *
     * 归因（`reason`，同时喂连通性提示）：Logto SDK 构造多数 `LogtoException` 时不透传底层
     * `Throwable`（如授权码换 token 失败直接传 `null` cause），故「超时 vs 网络」通常只能落到同一
     * `NETWORK` 桶，只有 cause 恰好保留时才进一步区分为 `TIMEOUT`。
     *
     * 埋点属性（用于登录漏斗排障）：`reason` 粗粒度类别做漏斗看板；`logto_type` 为 Logto 原始异常
     * 类型名（`LogtoException.message` 即 `Type.name()`），保留细粒度；`cause` 底层异常类名，尽力而为。
     */
    private fun analyzeSignInFailure(exception: LogtoException): Pair<SignInFailureReason, Map<String, Any>> {
        val logtoType = exception.message ?: LogtoException::class.java.simpleName
        val causeChain = generateSequence(exception.cause) { it.cause }.toList()
        val reason = when {
            logtoType == LogtoException.Type.USER_CANCELED.name -> SignInFailureReason.USER_CANCELED
            // 时钟偏差：id_token 的 iat/exp 校验被 jose4j 拒绝（容差 60s）。必须先于网络类判定——
            // 重试永远失败，通用提示会把用户引进死循环（2026-07-22 模拟器慢 63s 实测的坑）
            isClockSkew(causeChain) -> SignInFailureReason.CLOCK_SKEW
            causeChain.any { it is SocketTimeoutException } -> SignInFailureReason.TIMEOUT
            causeChain.any { it is UnknownHostException || it is ConnectException || it is IOException } -> SignInFailureReason.NETWORK
            logtoType in NETWORK_LOGTO_TYPES -> SignInFailureReason.NETWORK
            logtoType == LogtoException.Type.UNABLE_TO_LAUNCH_BROWSER.name -> SignInFailureReason.NO_BROWSER
            logtoType in CONFIG_LOGTO_TYPES -> SignInFailureReason.CONFIG
            else -> SignInFailureReason.OTHER
        }
        val props = buildMap<String, Any> {
            put("reason", reason.name.lowercase())
            put("logto_type", logtoType)
            causeChain.lastOrNull()?.let { put("cause", it::class.java.simpleName) }
        }
        return reason to props
    }

    override fun signOut() {
        // 完整登出：SDK 先撤销 refresh token，再经系统浏览器结束 Logto 会话、回跳 app。
        // 本地凭证在 signOut 启动时即同步清除，故可立即置登出态；远端/浏览器步骤为尽力而为、失败不阻塞。
        // 无可用 Activity 时（如后台错误处理）退回本地登出。
        val activity = activityRef.get()
        if (activity != null) {
            logtoClient.signOut(activity, REDIRECT_URI) { /* 浏览器/撤销失败不阻塞本地登出 */ }
        } else {
            logtoClient.clearCredentials { /* 本地凭证已清除即视为登出 */ }
        }
        globalSettingsManager.setUserAvatarUrl(null)
        globalSettingsManager.setGithubIdentity(null, null)
        globalSettingsManager.setUserEmail(null)
        globalSettingsManager.setIsPro(false)
        globalSettingsManager.clearSelectedChatModel()
        GithubTokenProvider.shared.clear()
        FollowingProvider.shared.clear()
        OwnRepoEventsProvider.shared.clear()
        _authState.value = AuthState.LoggedOut
        trackEvent("sign_out")
    }

    override suspend fun getAccessToken(): String? {
        // 竞态由 SDK 的 CredentialGuard 兜底：登出后的在途刷新会以 NOT_AUTHENTICATED 收尾、不写回。
        if (!logtoClient.isAuthenticated) return null
        return suspendCancellableCoroutine { cont ->
            logtoClient.getAccessToken { exception, accessToken ->
                exception?.let { maybeHintRefreshClockSkew(it) }
                cont.resume(accessToken?.token)
            }
        }
    }

    /**
     * 静默刷新失败若是时钟偏差，重试/重登都救不回来（新 token 的 id_token 校验同样失败），而 app 仍
     * 自认已登录、请求被服务端按匿名处理（authDegraded）——用户看到的「重试」提示是死循环。
     * 弹一次时钟修复提示（复用 [SignInFailureBus] 的 CLOCK_SKEW 通道），进程内只弹一次防打扰
     * （getAccessToken 每次请求都会调）。
     */
    private fun maybeHintRefreshClockSkew(exception: LogtoException) {
        val causeChain = generateSequence(exception.cause) { it.cause }.toList()
        if (!isClockSkew(causeChain)) return
        if (clockSkewHinted.compareAndSet(false, true)) {
            trackEvent("token_refresh_clock_skew", emptyMap())
            SignInFailureBus.emit(SignInFailureReason.CLOCK_SKEW)
        }
    }

    /**
     * jose4j 的时间类校验失败即时钟偏差：iat 在「未来」（23，本机偏慢）或刚签发即「过期」（1，本机
     * 偏快超过 token 有效期）。id_token 是刚从服务端换来的，这两种拒绝只可能是本机时钟不准。
     */
    private fun isClockSkew(causeChain: List<Throwable>): Boolean = causeChain.any {
        it is InvalidJwtException &&
            (it.hasErrorCode(ErrorCodes.ISSUED_AT_INVALID_FUTURE) || it.hasErrorCode(ErrorCodes.EXPIRED))
    }

    /**
     * sign_in_error 上报前异步补测服务器-本机时钟偏差（任一 HTTPS 响应的 Date 头即可，取 Logto 端点）：
     * `clock_offset_ms` 正值 = 本机偏慢。让 Aptabase 面板能直接回答「真实用户里有多少时钟不准」。
     * 探测失败（离线等）不带该属性照常上报；后台线程执行，不阻塞失败回调。
     */
    private fun trackSignInError(props: Map<String, Any>) {
        thread(isDaemon = true, name = "sign-in-clock-probe") {
            val offset = measureClockOffsetMs()
            trackEvent("sign_in_error", if (offset != null) props + ("clock_offset_ms" to offset) else props)
        }
    }

    private fun measureClockOffsetMs(): Long? = runCatching {
        val connection = URL(LOGTO_ENDPOINT).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "HEAD"
            connection.connectTimeout = CLOCK_PROBE_TIMEOUT_MS
            connection.readTimeout = CLOCK_PROBE_TIMEOUT_MS
            val serverDate = connection.getHeaderFieldDate("Date", 0L)
            if (serverDate == 0L) null else serverDate - System.currentTimeMillis()
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    /**
     * 登录发起时的 Logto 端点可达性探测（异步 fire-and-forget）：托管页/授权端点在 Custom Tab 里
     * 加载失败时 SDK 收不到任何回调，只会表现为「用户取消」——可达性失败被系统性伪装成取消，
     * 76% 取消率只能靠推测归因大陆可达性就是这个盲区造成的。
     * 分析口径：可达性失败占比 ≈ auth_probe 失败率，用「probe 失败 × cancel_kind=wait」交叉验证。
     *
     * 任何 HTTP 状态码（含 4xx/5xx）都算 ok——只测「链路能不能通」，不测服务对错。
     */
    private fun trackAuthProbe(method: String) {
        thread(isDaemon = true, name = "auth-probe") {
            val startedAt = SystemClock.elapsedRealtime()
            val result = try {
                val connection = URL(LOGTO_ENDPOINT).openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "HEAD"
                    connection.connectTimeout = AUTH_PROBE_TIMEOUT_MS
                    connection.readTimeout = AUTH_PROBE_TIMEOUT_MS
                    connection.responseCode
                    "ok"
                } finally {
                    connection.disconnect()
                }
            } catch (_: SocketTimeoutException) {
                "timeout"
            } catch (_: Exception) {
                "fail"
            }
            val latencyMs = SystemClock.elapsedRealtime() - startedAt
            trackEvent(
                "auth_probe",
                mapOf(
                    "result" to result,
                    "method" to method,
                    "latency_bucket" to probeLatencyBucket(latencyMs),
                ),
            )
        }
    }

    companion object {
        private const val TAG = "LogtoAuth"
        private const val LOGTO_APP_ID = "lasqslwwdjbim73vgkapj"
        private const val CLOCK_PROBE_TIMEOUT_MS = 3_000
        private const val AUTH_PROBE_TIMEOUT_MS = 5_000

        /** 托管页 first_screen / identifier 参数值（Logto OIDC 授权端点约定） */
        private const val FIRST_SCREEN_IDENTIFIER_SIGN_IN = "identifier:sign_in"
        private const val IDENTIFIER_EMAIL = "email"

        /** auth_probe 延迟分桶：粒度比登录耗时桶细——探测本身 5s 超时，关注的是「页面级资源会不会太慢」 */
        private fun probeLatencyBucket(latencyMs: Long): String = when {
            latencyMs < 300 -> "lt_300ms"
            latencyMs < 1_000 -> "300_1000ms"
            latencyMs < 2_500 -> "1000_2500ms"
            latencyMs < 5_000 -> "2500_5000ms"
            else -> "gte_5s"
        }

        /**
         * 长短取消的分界：短于此为 `quick`（授权页正常加载后主动拒绝），长于此为 `wait`
         * （等待/挣扎后放弃，体感上多为授权页加载不出的网络性失败）。取 10s 依据 2026-07
         * Aptabase 实测取消耗时双峰——短峰中位 3.1s、长峰中位 33.7s，10s 落在两峰谷底。
         */
        private const val CANCEL_KIND_QUICK_THRESHOLD_MS = 10_000L

        /**
         * 耗时预分桶：Aptabase 把属性值当字符串枚举分组，原始 duration_ms 每个值都唯一、
         * 分布在面板上直接碎掉，只能导 CSV 手工分析；客户端先归入固定枚举桶才能直接看分布。
         */
        private fun durationBucket(durationMs: Long): String = when {
            durationMs < 5_000 -> "lt_5s"
            durationMs < 15_000 -> "5_15s"
            durationMs < 45_000 -> "15_45s"
            durationMs < 120_000 -> "45_120s"
            else -> "gte_120s"
        }

        /** 远端拉取失败——网络不可达 / 服务端异常，归为 `network`。 */
        private val NETWORK_LOGTO_TYPES = setOf(
            LogtoException.Type.UNABLE_TO_FETCH_OIDC_CONFIG.name,
            LogtoException.Type.UNABLE_TO_FETCH_TOKEN_BY_AUTHORIZATION_CODE.name,
            LogtoException.Type.UNABLE_TO_FETCH_USER_INFO.name,
            LogtoException.Type.UNABLE_TO_FETCH_JWKS_JSON.name,
        )

        /** 回跳 / 重定向配置错误，归为 `config`。 */
        private val CONFIG_LOGTO_TYPES = setOf(
            LogtoException.Type.INVALID_REDIRECT_URI.name,
            LogtoException.Type.INVALID_CALLBACK_URI.name,
        )

        /**
         * release: cn.trendingai://whl.trending.ai/callback；debug 包名带 .debug，两条均已在 Logto 注册。
         * 同时用作登录回跳与登出后回跳（post sign-out redirect URI）。scheme 与 manifestPlaceholder
         * `logtoRedirectScheme` 共用 BuildConfig.LOGTO_REDIRECT_SCHEME 单一来源（见 androidApp/build.gradle.kts）。
         */
        private val REDIRECT_URI = "${BuildConfig.LOGTO_REDIRECT_SCHEME}://${BuildConfig.APPLICATION_ID}/callback"
    }
}
