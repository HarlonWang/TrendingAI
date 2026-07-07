package whl.trending.ai.core

import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import whl.trending.ai.core.platform.openUrl
import whl.trending.ai.data.local.globalSettingsManager

/**
 * Pro 赞助页统一入口 + 权益对账窗口。
 *
 * 打开赞助页必须经 [openSponsorPage]：它记录赞助意图时间戳，并强制在应用外打开
 * （Custom Tab / 系统浏览器，不传 onInAppFallback）——一是 GitHub Sponsors 依赖
 * 浏览器的 github.com 登录态，内置 WebView 里没有；二是保证返回 app 时必然触发
 * ON_RESUME，前台对账（MainActivity.onResume → refreshPro）才有机会执行。
 *
 * 对账只在 [shouldReconcile] 的窗口内进行：没打开过赞助页的用户回前台零后端请求
 * （服务端 pro-refresh 每次都要消耗 GitHub PAT 配额，不能拿 resume 当轮询点）。
 */
object ProSponsor {

    /** 打开赞助页后允许对账的时间窗口：覆盖赞助生效的「几分钟内」延迟，又给请求数封顶。 */
    private val RECONCILE_WINDOW = 30.minutes

    fun openSponsorPage() {
        globalSettingsManager.setSponsorPageOpenedAt(Clock.System.now().toEpochMilliseconds())
        openUrl(Constants.GITHUB_SPONSORS_URL)
    }

    /** 是否处于「刚打开过赞助页」的对账窗口内。 */
    fun shouldReconcile(): Boolean {
        val openedAt = globalSettingsManager.getSponsorPageOpenedAtSync()
        return openedAt > 0 &&
            Clock.System.now().toEpochMilliseconds() - openedAt < RECONCILE_WINDOW.inWholeMilliseconds
    }

    /** 对账确认已是 Pro 后调用，结束窗口。 */
    fun markReconciled() {
        globalSettingsManager.clearSponsorPageOpenedAt()
    }
}
