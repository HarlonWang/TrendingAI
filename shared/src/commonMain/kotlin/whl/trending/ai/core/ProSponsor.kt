package whl.trending.ai.core

import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import whl.trending.ai.core.analytics.AppEvent
import whl.trending.ai.core.analytics.UpsellTarget
import whl.trending.ai.core.analytics.track
import whl.trending.ai.core.platform.openUrl
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.data.model.ProRefreshResponse

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

    /**
     * upsell 点击（upsell_clicked）的 source 词汇，集中在此避免各入口自造事件名
     * 导致漏斗无法统一查询。新增赞助入口时在这里登记。
     *
     * 2026-07-26 起 chat 侧不再做 Pro 引导（配额触顶卡与模型锁定弹窗都降为纯提示），
     * 曝光埋点 pro_upsell_shown 随之下线——赞助入口只剩设置页与账户页，均为用户主动点击，
     * 「曝光」无从定义。历史 shown 事件的数据边界止于此日期，做漏斗时注意。
     */
    const val SOURCE_SETTINGS_LANGUAGE = "settings_language"
    const val SOURCE_SETTINGS_DONATE = "settings_donate"
    // SOURCE_ACCOUNT 已随账户页「升级 Pro」改指 Paddle 订阅页而移除（见 ProCheckout）。
    // 现存两个入口都是「支持项目」语义而非买权益，继续走 Sponsors 是对的：
    // 语言支持请求后的赞助引导、关于页的捐赠。

    /**
     * 打开赞助页统一入口。[upsellSource] 非空时上报统一的 upsell_clicked——各入口
     * 不再自报点击事件，clicked→refreshPro 漏斗才能按同一词汇横向对比。
     */
    fun openSponsorPage(upsellSource: String? = null) {
        upsellSource?.let { track(AppEvent.UpsellClicked(source = it, target = UpsellTarget.SPONSOR)) }
        globalSettingsManager.setSponsorPageOpenedAt(Clock.System.now().toEpochMilliseconds())
        openUrl(Constants.GITHUB_SPONSORS_URL)
    }

    /** 是否处于「刚打开过赞助页」的对账窗口内。 */
    fun shouldReconcile(): Boolean {
        val openedAt = globalSettingsManager.currentSponsorPageOpenedAt()
        return openedAt > 0 &&
            Clock.System.now().toEpochMilliseconds() - openedAt < RECONCILE_WINDOW.inWholeMilliseconds
    }

    /** 对账确认已是 Pro 后调用，结束窗口。 */
    fun markReconciled() {
        globalSettingsManager.clearSponsorPageOpenedAt()
    }

    /**
     * 「去过赞助页回来，账户却没关联 GitHub」信号。
     *
     * 注意这是**启发式**：合取的两个信号里，「去过赞助页」来自本地时间戳（[shouldReconcile]）
     * 只能表达意图，「没关联 GitHub」才是后端事实——后端没有 github_user_id 可查，
     * 无从知道这人到底付没付钱。因此宿主文案不得断言「赞助已收到」，详见 SponsorLinkHost。
     *
     * 这是 2026-07-29 首位赞助者被拦 48 分钟的修复面：当时对账返回的是裸 `pro:false`，
     * 与「查证后确实没赞助」完全同形，客户端无从区分只能沉默，用户付完钱又连撞 8 次配额墙，
     * 最后自己摸到账户页的「关联 GitHub」才解套。后端现在会带 `reason` 把两者分开。
     *
     * 走总线 + 根部宿主（`SponsorLinkHost`）而不是在对账处直接弹窗：对账发生在 Activity 的
     * ON_RESUME，那里没有 composition，且用户从浏览器回来时可能停在任意页面。
     * replay=1 保证「宿主尚未订阅就已 emit」不丢事件，代价是必须 [consumeNeedsGithubLink]。
     */
    private val _needsGithubLink = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1)
    val needsGithubLink: SharedFlow<Unit> = _needsGithubLink.asSharedFlow()

    fun signalNeedsGithubLink() {
        _needsGithubLink.tryEmit(Unit)
    }

    /** 收到即消费：清 replay 缓存，避免重建的收集者（旋转/主题切换）把同一次事件再弹一遍。 */
    fun consumeNeedsGithubLink() {
        _needsGithubLink.resetReplayCache()
    }

    /**
     * 对账结果该触发什么动作。抽成纯函数是因为它是 P0-4 的判定核心，
     * 而调用点在 Activity 的 ON_RESUME 里、测不到。
     *
     * [STAY_SILENT] 覆盖 `not_sponsor`（确实没赞助）、`lookup_failed`（查不到）与请求失败：
     * 这三种都**不该向用户断言任何事**，尤其查询失败时说「你没赞助」会把真赞助者气走。
     */
    fun reconcileAction(result: ProRefreshResponse?): ReconcileAction = when {
        result?.pro == true -> ReconcileAction.MARK_PRO
        result?.reason == ProRefreshResponse.REASON_GITHUB_NOT_LINKED -> ReconcileAction.GUIDE_LINK
        else -> ReconcileAction.STAY_SILENT
    }
}

/** 见 [ProSponsor.reconcileAction]。 */
enum class ReconcileAction {
    /** 已是 Pro：结束对账窗口。 */
    MARK_PRO,

    /** 钱付了但没关联 GitHub：结束窗口并弹引导——唯一需要主动开口的分支。 */
    GUIDE_LINK,

    /** 什么都不做，窗口留着下次回前台再试。 */
    STAY_SILENT,
}
