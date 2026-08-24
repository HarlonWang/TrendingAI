package whl.trending.ai.core

import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import whl.trending.ai.core.analytics.AppEvent
import whl.trending.ai.core.analytics.CheckoutStepKind
import whl.trending.ai.core.analytics.track
import whl.trending.ai.core.platform.openUrl
import whl.trending.ai.data.local.globalSettingsManager

/**
 * Paddle 收银台外跳 + 权益回流对账。与 [ProSponsor] 骨架相同但**刻意不合并**：
 * 对账端点、失败语义、时序都不同——Paddle 权益要等 webhook 落库，必须轮询而不是查一次。
 */
object ProCheckout {

    /** 打开收银台后允许对账的时间窗口，给请求数封顶。 */
    private val RECONCILE_WINDOW = 30.minutes

    /** 重查节奏：首次立即，递增退避覆盖 webhook 延迟；再久交给窗口内下一次回前台。 */
    private val RETRY_DELAYS = listOf(0.seconds, 3.seconds, 8.seconds, 15.seconds)

    /** 埋点 source 词汇。新增购买入口时在这里登记，否则漏斗按 source 分组会漏。 */
    const val SOURCE_ACCOUNT = "account"

    const val PLAN_ANNUAL = "annual"
    const val PLAN_MONTHLY = "monthly"

    /**
     * 打开收银台。**必须经此函数**：落购买意图时间戳，[reconcile] 才知道该对账。
     * 强制应用外打开——内置 WebView 跑不通 Paddle 三方支付跳转，且外跳才保证回来触发 ON_RESUME。
     */
    fun openCheckout(url: String, plan: String) {
        track(AppEvent.CheckoutStep(CheckoutStepKind.OPENED, plan = plan))
        globalSettingsManager.setCheckoutOpenedAt(Clock.System.now().toEpochMilliseconds())
        openUrl(url)
    }

    /** 是否处于「刚去过收银台」的对账窗口内。 */
    fun shouldReconcile(): Boolean {
        val openedAt = globalSettingsManager.currentCheckoutOpenedAt()
        return openedAt > 0 &&
            Clock.System.now().toEpochMilliseconds() - openedAt < RECONCILE_WINDOW.inWholeMilliseconds
    }

    /** 对账确认权益到账后调用，结束窗口。 */
    fun markReconciled() {
        globalSettingsManager.clearCheckoutOpenedAt()
    }

    /**
     * 回前台对账：按 [RETRY_DELAYS] 重查权益，直到到账或次数用尽。
     * 刻意只做「轮询 + 判定」不带副作用——调用点在 ON_RESUME 里测不到，判定必须能单独跑。
     * 返回第几次尝试拿到权益（1 起）；未到账返回 null，此时**窗口刻意不清**、不弹提示——
     * 付款可能只是没走完，此刻断言任何事都可能是错的。
     */
    suspend fun reconcile(refreshPro: suspend () -> Boolean): Int? {
        RETRY_DELAYS.forEachIndexed { index, wait ->
            if (wait > 0.seconds) delay(wait)
            // 单次失败不中断整轮；CancellationException 必须放行——吞掉会把「协程已取消」
            // 退化成「这次没查到」，取消再也传不出去。
            val pro = try {
                refreshPro()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            if (pro) return index + 1
        }
        return null
    }

    /** 到账后的收尾：结束对账窗口并通知宿主弹「Pro 已开通」。埋点由调用方按 attempt 上报。 */
    fun markActivated() {
        markReconciled()
        _activated.tryEmit(Unit)
    }

    /**
     * 「订阅已到账」信号。ON_RESUME 处无 composition，走总线由根部宿主 `CheckoutResultHost` 弹；
     * replay=1 防宿主未订阅丢事件，代价是必须 [consumeActivated]。
     */
    private val _activated = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1)
    val activated: SharedFlow<Unit> = _activated.asSharedFlow()

    /** 收到即消费：清 replay 缓存，避免重建的收集者把同一次事件再弹一遍。 */
    fun consumeActivated() {
        _activated.resetReplayCache()
    }
}
