package whl.trending.ai.core

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
     * 是否该显示提示：**有登录痕迹 && 当前未登录 && 没展示过**。三个条件由调用方分别
     * 取值后喂进来——不在这里代劳，是因为**登录态必须响应式地读**：
     *
     * 登录态恢复是异步的（`AuthClient.restore()` 经 `Dispatchers.IO` 读令牌），而首页列表
     * 首帧就组合、不等数据。若在此处读 `authState.value` 的快照，几乎必然读到尚未恢复的
     * LoggedOut，已登录用户会被告知「账号已升级、请重新登录」，且快照驻留后卡片还不会
     * 自己消失。拆开之后，UI 用 collectAsState 读登录态，恢复一到卡片即消失。
     *
     * 另两个条件（[currentShown] / [hasLoginTrace]）都是落盘数据，进程内求值一次即可。
     *
     * 判定只用于决定要不要显示一张可关闭的卡片，不参与鉴权：误判的最坏后果是多显示
     * 或少显示一次提示。
     */
    fun decide(shown: Boolean, loggedIn: Boolean, hasTrace: Boolean): Boolean =
        !shown && !loggedIn && hasTrace

    /** 提示是否已经展示过（落盘，一次性） */
    fun currentShown(): Boolean = globalSettingsManager.currentUpgradeNoticeShown()

    /**
     * 登录痕迹**只认 App 自有数据**——缓存过的 /api/me 资料（GitHub 身份或邮箱）。
     *
     * 曾经还有第二条信号「Logto SDK 遗留存储文件存在」，已删除：它证明的只是
     * 「打开过旧版 App」，而非「登录过」。旧版 MainActivity 无条件构造 LogtoClient，
     * 其 `init` 会给 refreshToken/idToken 赋值，而这两个属性的 setter 无条件回写
     * storage（未登录时是 `remove(key) + apply()`）——文件很可能对每个存量用户都存在，
     * 那就会把「重新登录即可恢复你的账号」推给从没有过账号的匿名用户。
     * 它能额外覆盖的只是「登录过但 /api/me 一次都没同步成功」的极小人群，不值这个风险。
     */
    fun hasLoginTrace(): Boolean {
        val s = globalSettingsManager
        return s.currentGithubLogin() != null ||
            s.currentGithubUserId() != null ||
            s.currentUserEmail() != null
    }

    /** 用户看过（或点了去登录）即标记，之后不再出现 */
    fun markShown() {
        globalSettingsManager.setUpgradeNoticeShown(true)
    }
}
