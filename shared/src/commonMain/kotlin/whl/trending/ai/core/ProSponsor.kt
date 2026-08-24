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
 * Pro 赞助页统一入口 + 权益对账窗口。打开赞助页必须经 [openSponsorPage]：记意图时间戳，
 * 并强制应用外打开——Sponsors 依赖浏览器的 github.com 登录态，且外跳才保证回来触发 ON_RESUME 对账。
 * 对账只在 [shouldReconcile] 窗口内：pro-refresh 烧 GitHub PAT 配额，不能拿 resume 当轮询点。
 */
object ProSponsor {

    /** 打开赞助页后允许对账的时间窗口：覆盖赞助生效延迟，又给请求数封顶。 */
    private val RECONCILE_WINDOW = 30.minutes

    /** upsell_clicked 的 source 词汇，新增赞助入口在此登记，别在调用点自造。 */
    const val SOURCE_SETTINGS_LANGUAGE = "settings_language"
    const val SOURCE_SETTINGS_DONATE = "settings_donate"
    // 这两个入口是「支持项目」语义而非买权益，走 Sponsors 而非 Paddle 订阅。

    /** 打开赞助页统一入口。[upsellSource] 非空时上报 upsell_clicked，各入口不再自报点击事件。 */
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
     * 「去过赞助页回来，账户却没关联 GitHub」信号。判定是启发式的，宿主文案不得断言「赞助已收到」。
     * ON_RESUME 处无 composition，走总线由根部宿主 `SponsorLinkHost` 弹；
     * replay=1 防宿主未订阅丢事件，代价是必须 [consumeNeedsGithubLink]。
     */
    private val _needsGithubLink = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1)
    val needsGithubLink: SharedFlow<Unit> = _needsGithubLink.asSharedFlow()

    fun signalNeedsGithubLink() {
        _needsGithubLink.tryEmit(Unit)
    }

    /** 收到即消费：清 replay 缓存，避免重建的收集者把同一次事件再弹一遍。 */
    fun consumeNeedsGithubLink() {
        _needsGithubLink.resetReplayCache()
    }

    /**
     * 对账结果该触发什么动作。抽成纯函数：调用点在 ON_RESUME 里测不到，判定必须能单独跑。
     * [STAY_SILENT] 覆盖没赞助/查不到/请求失败三种——都不该向用户断言任何事，
     * 查询失败时说「你没赞助」会把真赞助者气走。
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
