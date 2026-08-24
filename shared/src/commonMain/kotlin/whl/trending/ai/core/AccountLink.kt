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
 * 关联 GitHub 身份的入口。Pro 权益以 GitHub 数字 ID 为唯一键发放，邮箱登录用户没有
 * `github_user_id`，不先关联直接去赞助会「钱付了但权益对不上」。
 * 回跳结果（`?linked=github` / `?error=<reason>`）由 [whl.trending.ai.ui.common.OAuthOutcomeHost] 消费。
 */
object AccountLink {

    /** 入口来源词汇，用于 account_link_* 漏斗按入口拆分。 */
    const val SOURCE_ACCOUNT = "account"
    const val SOURCE_UPGRADE_DIALOG = "upgrade_dialog"

    /**
     * 尚未收到回跳的绑定的发起 source。登录失败与绑定失败的回跳形状相同，仅凭 URL 分不出来，
     * 靠它把失败事件分派给正确的处理方。**落盘而非内存变量**：授权期间进程可能被回收、
     * 回跳时是冷启动，内存标记会丢，绑定失败会被误判成登录失败。
     */
    private var pendingSource: String?
        get() = globalSettingsManager.accountLinkSource()
        set(value) = globalSettingsManager.setAccountLinkSource(value)

    /**
     * 发起绑定。浏览器环节归 loginbase-kt-browser，授权阶段的失败从 `client.oauthResults` 送达宿主；
     * **发起阶段**的失败到不了那条通道（浏览器还没开），走 [launchFailed] 汇到同一个宿主提示。
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

    /** 发起阶段失败的信号，由 [whl.trending.ai.ui.common.OAuthOutcomeHost] 弹提示。 */
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

    /** 取走本次绑定流程的发起 source（一次性）。返回非 null 即「这次回跳属于绑定流程」。 */
    fun consumePendingSource(): String? = pendingSource.also { pendingSource = null }

    /**
     * 关联成功信号。账户页的 ProfileViewModel 不会自己重拉——没有它，
     * 身份已绑好、界面却仍停在「关联 GitHub」。
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
