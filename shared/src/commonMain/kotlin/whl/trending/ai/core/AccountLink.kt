package whl.trending.ai.core

import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import whl.trending.ai.auth.LOGTO_ENDPOINT
import whl.trending.ai.core.platform.getSystemLanguage
import whl.trending.ai.core.platform.openUrl
import whl.trending.ai.core.platform.trackEvent
import whl.trending.ai.data.local.globalSettingsManager

/**
 * 关联 GitHub 身份的入口 + 回前台刷新窗口。
 *
 * 背景：Pro 权益以 GitHub 数字 ID 为唯一键发放（后端 `pro_entitlements`），邮箱登录用户
 * 的 Logto 账户没有 github identity，直接去赞助会「钱付了但权益对不上」。此处引导他们先
 * 把 GitHub 关联到当前账户，关联后 identity 一挂上，Pro 判定与 GitHub 能力全部自动打通。
 *
 * 关联页是 Logto 预构建的单任务流程（见 [Constants.accountLinkGithubUrl]），必须在应用外
 * 打开——它依赖浏览器侧的 Logto 会话 cookie，内置 WebView 里没有。因此与赞助页同构：
 * 出去做事 → 用户手动返回 → ON_RESUME 时在 [shouldRefreshIdentity] 窗口内刷新身份。
 */
object AccountLink {

    /** 打开关联页后允许刷新身份的时间窗口：够走完邮箱验证码 + GitHub 授权，又给请求数封顶。 */
    private val REFRESH_WINDOW = 30.minutes

    /** 入口来源词汇，用于 account_link_* 漏斗按入口拆分。 */
    const val SOURCE_ACCOUNT = "account"
    const val SOURCE_UPGRADE_DIALOG = "upgrade_dialog"

    /** 打开 Logto 账户中心的 GitHub 关联页，并开启回前台刷新窗口。 */
    fun openLinkGithubPage(source: String) {
        trackEvent("account_link_start", mapOf("source" to source))
        globalSettingsManager.setAccountLinkOpenedAt(Clock.System.now().toEpochMilliseconds())
        openUrl(Constants.accountLinkGithubUrl(LOGTO_ENDPOINT, uiLocale()))
    }

    /**
     * Logto 页面语言：跟 App 语言走，「跟随系统」时回落到系统语言。
     * 中文必须给 `zh-CN`——实测传裸 `zh` 时 Logto 匹配不到语言包，静默回落英文。
     */
    private fun uiLocale(): String {
        val iso = globalSettingsManager.currentAppLanguage().isoCode ?: getSystemLanguage()
        return if (iso.startsWith("zh")) "zh-CN" else iso
    }

    /** 是否处于「刚打开过关联页」的窗口内——没去关联过的用户回前台零后端请求。 */
    fun shouldRefreshIdentity(): Boolean {
        val openedAt = globalSettingsManager.currentAccountLinkOpenedAt()
        return openedAt > 0 &&
            Clock.System.now().toEpochMilliseconds() - openedAt < REFRESH_WINDOW.inWholeMilliseconds
    }

    /** 确认身份已带上 GitHub 后调用，结束窗口。 */
    fun markLinked() {
        trackEvent("account_link_success")
        globalSettingsManager.clearAccountLinkOpenedAt()
    }
}
