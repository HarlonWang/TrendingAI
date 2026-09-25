package whl.trending.ai.tinyui

import app.tinyui.AnalyticsSink
import app.tinyui.HttpChannel
import app.tinyui.HttpRequest
import app.tinyui.KtorChannel
import app.tinyui.LinkOpener
import app.tinyui.PageError
import app.tinyui.PageSink
import app.tinyui.Session
import app.tinyui.SessionSource
import app.tinyui.TinyUIHost
import app.tinyui.components.registerBuiltins
import app.tinyui.schema.ComponentRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import okio.Path
import whl.trending.ai.auth.AuthState
import whl.trending.ai.auth.globalAuthManager
import whl.trending.ai.core.ProCheckout
import whl.trending.ai.core.analytics.trackPageEvent
import whl.trending.ai.core.platform.openUrl
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.data.remote.TrendingApi

/**
 * 宿主对 TinyUI 页面的承诺变了（宿主组件、`host.call` 能力、网络通道、是否提供会话、tinyui 下限、引擎）就加 1，
 * 并生成 `shared/tinyui-host/<n>.txt`（HostSnapshotTest，tinyui docs/updates.md §4.1）；单纯升 tinyui 不加。
 * 页面源码在 ../trendingai-tinyui。快照只认名字，`app` 通道带的身份与请求头变了测试不会拦、同样要加 1。
 */
const val HOST_VERSION = "6"

/** 订阅页打开收银台后发来，宿主据此开始对账（ProCheckout.reconcile） */
private const val CHECKOUT_OPENED = "trendingai.checkout.opened"

object TrendingTinyUI {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** App 级，全 App 一份（tinyui docs/native-api.md §1）：页面要的通用能力都由框架实现，这里只接 App 自己的东西 */
    val host: TinyUIHost by lazy {
        build(
            locale = globalSettingsManager.uiLanguageFlow.stateIn(scope, SharingStarted.Eagerly, globalSettingsManager.uiLanguage()),
            // 恢复会话前（Unknown）按未登录给页面，与原生侧「判 is LoggedIn」的口径一致
            session = globalAuthManager.authState.map { Session(it is AuthState.LoggedIn, null) }
                .stateIn(scope, SharingStarted.Eagerly, Session(globalAuthManager.authState.value is AuthState.LoggedIn, null)),
            dataDir = tinyuiUpdatesDir().parent!! / "tinyui-data",
        ).also { host ->
            host.events.subscribe(CHECKOUT_OPENED) { ProCheckout.markOpened() }
        }
    }

    /** 宿主契约（快照）只看组件、能力名、通道名与有没有会话；HostSnapshotTest 以假的状态源调它，不碰 App 单例 */
    internal fun build(locale: StateFlow<String>, session: StateFlow<Session>, dataDir: Path?) = TinyUIHost(
        components = ComponentRegistry().registerBuiltins(),
        sink = object : PageSink {
            override fun error(error: PageError) = println("TinyUI $error")
            override fun log(line: String) = println("TinyUI $line")
        },
        channels = mapOf("app" to appChannel()),
        locale = locale,
        session = SessionSource(session) { source -> withContext(Dispatchers.Main) { globalAuthManager.signIn(source) } },
        analytics = AnalyticsSink { name, props, _ -> trackPageEvent(name, props) },
        links = LinkOpener { url, _ -> openUrl(url) },
        dataDir = dataDir,
    )

    /** 与原生请求同一个 client（登录态、刷新、UA），另加安装标识：服务端按它记匿名额度与成单归因 */
    private fun appChannel(): HttpChannel {
        val ktor by lazy { KtorChannel(TrendingApi.sharedClient) }
        return HttpChannel { request ->
            ktor.request(HttpRequest(request.method, request.url, request.headers + ("X-Install-Id" to globalSettingsManager.getOrCreateInstallId()), request.body, request.timeoutMs))
        }
    }
}
