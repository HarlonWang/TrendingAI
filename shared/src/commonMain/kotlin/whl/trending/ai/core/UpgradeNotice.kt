package whl.trending.ai.core

import whl.trending.ai.auth.AuthState
import whl.trending.ai.auth.globalAuthManager
import whl.trending.ai.core.platform.hasLegacyLogtoArtifacts
import whl.trending.ai.data.local.globalSettingsManager

/**
 * 「账号系统已升级」的一次性提示（落地第 4 步的 C 方案，见 loginbase docs/plan.md）。
 *
 * 背景：新版不含 Logto 栈，升级后原本登录着的用户会以**干净未登录态**启动——这是
 * 主动设计的行为，不是故障，所以没有任何「会话过期」事件可依赖。而 TrendingAI 的
 * 主信息流匿名可用，登录只守收藏同步 / chat 配额 / Pro，于是：
 * - **不弹全屏登录引导**（会打扰纯匿名用户）；
 * - **也不静默降级**（「收藏悄悄不同步」最伤信任）。
 *
 * 取中间：静默登出 + 定向轻提示。
 *
 * **硬要求**：升级导致的未登录态**不得清除任何用户数据**，也不得复用「用户主动登出」
 * 的代码路径（那条会清收藏同步状态）。效果是未登录窗口期收藏照常可见（只是暂停同步），
 * 重登后 user_id 不变、云端全量拉取无缝接回。
 */
object UpgradeNotice {

    /**
     * 是否该显示提示：**有登录痕迹 && 当前未登录 && 没展示过**。
     *
     * 痕迹取两类信号，任一命中即可：
     * - App 自有数据——缓存过的 /api/me 资料（GitHub 身份或邮箱）；
     * - Logto SDK 的遗留存储文件**存在**（不解析内容，见 [hasLegacyLogtoArtifacts]）。
     *
     * 判定只用于决定要不要显示一张可关闭的卡片，不参与鉴权：误判的最坏后果是多显示
     * 或少显示一次提示。
     */
    fun shouldShow(): Boolean = decide(
        shown = globalSettingsManager.currentUpgradeNoticeShown(),
        loggedIn = globalAuthManager.authState.value is AuthState.LoggedIn,
        hasTrace = hasLoginTrace(),
    )

    /** 纯判定（三个条件都得成立），拆出来是为了可测——组合条件最容易写反 */
    internal fun decide(shown: Boolean, loggedIn: Boolean, hasTrace: Boolean): Boolean =
        !shown && !loggedIn && hasTrace

    private fun hasLoginTrace(): Boolean {
        val s = globalSettingsManager
        val appTrace = s.currentGithubLogin() != null ||
            s.currentGithubUserId() != null ||
            s.currentUserEmail() != null
        return appTrace || hasLegacyLogtoArtifacts()
    }

    /** 用户看过（或点了去登录）即标记，之后不再出现 */
    fun markShown() {
        globalSettingsManager.setUpgradeNoticeShown(true)
    }
}
