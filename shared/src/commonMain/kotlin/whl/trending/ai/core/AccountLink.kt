package whl.trending.ai.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import wang.harlon.eventbase.Eventbase
import whl.trending.ai.auth.LoginbaseAuthManager
import whl.trending.ai.auth.globalAuthManager
import whl.trending.ai.auth.launchGithubLink
import whl.trending.ai.core.analytics.AppEvent
import whl.trending.ai.core.analytics.AuthAction
import whl.trending.ai.core.analytics.AuthOutcome
import whl.trending.ai.core.analytics.track
import whl.trending.ai.data.local.globalSettingsManager

/**
 * 关联 GitHub 身份的入口。
 *
 * 背景：Pro 权益以 GitHub 数字 ID 为唯一键发放（后端 `pro_entitlements`），邮箱登录用户
 * 的账号没有 `github_user_id`，直接去赞助会「钱付了但权益对不上」——2026-07-29 首位
 * 赞助者就被这个坑拦了 48 分钟（见 SponsorLinkHost）。此处引导他们先把 GitHub 关联到
 * 当前账号，关联后 Pro 判定与 GitHub 能力全部自动打通。
 *
 * **2026-08-13 改造**：从 Logto 账户中心的预构建页换成 loginbase 的 link 流程
 * （`POST /oauth/github/link/start` → 系统浏览器授权 → deepLink 回跳）。
 *
 * 随之删掉的是一整套为 Logto 网页流程做的补偿：那个关联页是 web 单任务流程、
 * **回跳不了 App**，所以过去只能靠「用户手动返回 → ON_RESUME → 30 分钟窗口内刷新身份」
 * 去猜是否关联成功。现在 callback 直接 302 回 deepLink，结果是确定的——`?linked=github`
 * 或 `?error=<reason>`，由 [whl.trending.ai.ui.common.OAuthOutcomeHost] 消费。
 */
object AccountLink {

    /** 入口来源词汇，用于 account_link_* 漏斗按入口拆分。 */
    const val SOURCE_ACCOUNT = "account"
    const val SOURCE_UPGRADE_DIALOG = "upgrade_dialog"

    /**
     * 是否有一次由本入口发起、尚未收到回跳的绑定。
     *
     * 用途：协议里登录失败与绑定失败都回跳 `?error=`，两者形状相同（`internal` 两边都会
     * 出现），仅凭回跳 URL 分不出来；靠这个标记把失败事件分派给正确的处理方
     * （绑定失败归账户页提示，登录失败归登录面板）。存 source 而非裸布尔，顺带让终态
     * 埋点带得上发起入口。
     *
     * **落盘而非内存变量**：绑定要跳出去开系统浏览器，授权期间进程随时可能被系统回收，
     * 回跳时是冷启动。内存标记那时已经没了，绑定失败会被误判成登录失败、提示分派到错误
     * 的地方。落盘后跨进程存活。
     *
     * 更彻底的解法在服务端——link 分支的错误回跳自带 `mode=link`，客户端就完全不需要这个
     * 标记；已记入 loginbase 的协议待办，等那边落地后这里可以删掉。
     */
    private var pendingSource: String?
        get() = globalSettingsManager.accountLinkSource()
        set(value) = globalSettingsManager.setAccountLinkSource(value)

    /**
     * 发起绑定。浏览器环节归 loginbase-kt-browser：授权 URL 的换取（带 Bearer 的
     * link/start）在库的管理页内完成，网络失败与用户取消都会从
     * `client.oauthResults` 以 Failed / Cancelled 送达 [whl.trending.ai.ui.common.OAuthOutcomeHost]。
     *
     * **发起阶段**的失败（未初始化、无宿主 Activity）到不了那条通道——浏览器还没开起来。
     * 走 [launchFailed]，与授权阶段的失败汇到同一个宿主、同一个提示，调用方不必各自兜。
     */
    fun openLinkGithubPage(source: String) {
        val manager = globalAuthManager as? LoginbaseAuthManager
            ?: return failToLaunch("not_initialized", source)
        track(AppEvent.AuthStarted(AuthAction.LINK, method = "github", source = source), Eventbase.startFlow())
        pendingSource = source
        if (!launchGithubLink(manager.client)) {
            pendingSource = null
            failToLaunch("no_host_activity", source)
        }
    }

    /**
     * 发起阶段失败的信号，由 [whl.trending.ai.ui.common.OAuthOutcomeHost] 弹提示。
     *
     * 没有它的话调用方拿到的是一个「返回失败原因」的返回值——而两个入口都只是把它丢掉，
     * 用户点了「关联 GitHub」什么也不会发生。
     */
    private val _launchFailed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val launchFailed: SharedFlow<Unit> = _launchFailed

    private fun failToLaunch(reason: String, source: String?) {
        track(
            AppEvent.AuthFinished(
                AuthAction.LINK,
                AuthOutcome.ERROR,
                method = "github",
                source = source,
                reason = reason,
            ),
            Eventbase.currentFlow(),
        )
        _launchFailed.tryEmit(Unit)
    }

    /**
     * 取走本次绑定流程的发起 source（一次性）。返回非 null 即「这次回跳属于绑定流程」，
     * 同时把 source 交给终态事件——漏斗按入口分组要的就是它，而回跳时（可能已冷启动）
     * 只有落盘的这一份还在。
     */
    fun consumePendingSource(): String? = pendingSource.also { pendingSource = null }

    /**
     * 关联成功信号。刷新身份的是 [whl.trending.ai.ui.common.OAuthOutcomeHost]，而账户页的
     * ProfileViewModel 早已组合完毕、不会自己重拉——没有这个信号，身份已经绑好了，
     * 界面却仍停在「关联 GitHub」。
     */
    private val _linked = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val linked: SharedFlow<Unit> = _linked

    /** 确认身份已带上 GitHub 后调用：通知界面重载。[source] 由调用方从 [consumePendingSource] 取。 */
    fun markLinked(source: String?) {
        pendingSource = null
        track(
            AppEvent.AuthFinished(AuthAction.LINK, AuthOutcome.SUCCESS, method = "github", source = source),
            Eventbase.currentFlow(),
        )
        _linked.tryEmit(Unit)
    }
}
