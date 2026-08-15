package whl.trending.ai.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import whl.trending.ai.auth.LoginbaseAuthManager
import whl.trending.ai.auth.globalAuthManager
import whl.trending.ai.auth.launchGithubLink
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.core.platform.trackEvent

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
 * 或 `?error=<reason>`，由 [whl.trending.ai.ui.common.AccountLinkHost] 消费。
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
     * （绑定失败归账户页提示，登录失败归登录面板）。
     *
     * **落盘而非内存变量**：绑定要跳出去开系统浏览器，授权期间进程随时可能被系统回收，
     * 回跳时是冷启动。内存标记那时已经没了，绑定失败会被误判成登录失败、提示分派到错误
     * 的地方。落盘后跨进程存活。
     *
     * 更彻底的解法在服务端——link 分支的错误回跳自带 `mode=link`，客户端就完全不需要这个
     * 标记；已记入 loginbase 的协议待办，等那边落地后这里可以删掉。
     */
    private var pending: Boolean
        get() = globalSettingsManager.accountLinkPending()
        set(value) = globalSettingsManager.setAccountLinkPending(value)

    /**
     * 发起绑定。浏览器环节归 loginbase-kt-browser：授权 URL 的换取（带 Bearer 的
     * link/start）在库的管理页内完成，网络失败与用户取消都会从
     * `client.oauthResults` 以 Failed / Cancelled 送达 [whl.trending.ai.ui.common.AccountLinkHost]。
     *
     * @return 失败原因（未初始化、无宿主 Activity）；成功发起返回 null
     */
    fun openLinkGithubPage(source: String): Throwable? {
        val manager = globalAuthManager as? LoginbaseAuthManager
            ?: return IllegalStateException("loginbase auth not initialized")
        trackEvent("account_link_start", mapOf("source" to source))
        pending = true
        if (!launchGithubLink(manager.client)) {
            pending = false
            return IllegalStateException("no host activity for oauth")
        }
        return null
    }

    /** 取走「本次失败是否属于绑定流程」的标记（一次性） */
    fun consumePending(): Boolean = pending.also { pending = false }

    /**
     * 关联成功信号。刷新身份的是 [whl.trending.ai.ui.common.AccountLinkHost]，而账户页的
     * ProfileViewModel 早已组合完毕、不会自己重拉——没有这个信号，身份已经绑好了，
     * 界面却仍停在「关联 GitHub」。
     */
    private val _linked = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val linked: SharedFlow<Unit> = _linked

    /** 确认身份已带上 GitHub 后调用：通知界面重载。 */
    fun markLinked() {
        pending = false
        trackEvent("account_link_success")
        _linked.tryEmit(Unit)
    }
}
