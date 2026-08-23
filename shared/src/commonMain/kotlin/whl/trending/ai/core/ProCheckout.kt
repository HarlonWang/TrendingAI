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
 * Paddle 收银台外跳 + 权益回流对账。
 *
 * 与 [ProSponsor] 骨架相同（外跳 → 记意图时间戳 → 回前台对账）但**刻意不合并**：
 * 对账端点不同（Sponsors 走 `/api/pro/refresh` 要烧维护者 PAT 配额，Paddle 走 `/api/me`
 * 纯 D1 查询）；失败语义不同（Sponsors 有「付了钱但没关联 GitHub」要主动引导，Paddle 的
 * 身份键就是 `app_users.user_id`，不存在对不上号的情形）；时序不同（Paddle 权益要等
 * webhook 落库，用户可能比 webhook 先回 App，所以这里必须**轮询**而不是查一次）。
 */
object ProCheckout {

    /** 打开收银台后允许对账的时间窗口。付款可能中断在任何一步，窗口给请求数封顶。 */
    private val RECONCILE_WINDOW = 30.minutes

    /**
     * 回前台后的重查节奏：第一次立即查（多数情况 webhook 早已落库），之后递增退避覆盖
     * webhook 的常见延迟；再久就不该让用户对着转圈等，交给窗口内的下一次回前台。
     */
    private val RETRY_DELAYS = listOf(0.seconds, 3.seconds, 8.seconds, 15.seconds)

    /** 埋点 source 词汇。新增购买入口时在这里登记，否则漏斗按 source 分组会漏。 */
    const val SOURCE_ACCOUNT = "account"

    const val PLAN_ANNUAL = "annual"
    const val PLAN_MONTHLY = "monthly"

    /**
     * 打开收银台。**必须经此函数**：它落下购买意图时间戳，[reconcile] 才知道该对账。
     *
     * 强制在应用外打开（不传 onInAppFallback）：收银台要走 Paddle 的三方支付跳转与风控，
     * 内置 WebView 跑不通；且外跳才能保证付完回来必然触发 ON_RESUME。
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
     *
     * **刻意保持纯粹**——只做「轮询 + 判定」，不写 settings、不埋点、不发信号。
     * 副作用全部交给调用方（见 [markActivated]），与 [ProSponsor.reconcileAction] 同一范式：
     * 判定核心的调用点在 Activity 的 ON_RESUME 里、测不到，所以判定本身必须能单独跑。
     *
     * [refreshPro] 传入「刷新一次并返回是否已是 Pro」的动作，本对象不碰网络层。
     *
     * 返回第几次尝试拿到了权益（1 起，供埋点区分到账快慢），
     * 未到账返回 null——此时**窗口刻意不清**，用户下次回前台会再试一轮，
     * 且不弹任何提示：付款可能只是没走完，此刻断言任何事都可能是错的。
     */
    suspend fun reconcile(refreshPro: suspend () -> Boolean): Int? {
        RETRY_DELAYS.forEachIndexed { index, wait ->
            if (wait > 0.seconds) delay(wait)
            // 单次失败（网络抖动、token 刚过期）不该中断整轮：后面几次仍有机会拿到。
            // 但 CancellationException 必须放行——runCatching 连它一起吞，会让「协程已被
            // 取消」退化成「这次没查到」：最后一轮被取消时 reconcile 照常返回 null、
            // 外层协程正常完成，取消再也传不出去，且已被取消的请求还会白跑一次。
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
     * 「订阅已到账」信号。走总线 + 根部宿主（`CheckoutResultHost`）而不是就地弹窗：
     * 对账发生在 Activity 的 ON_RESUME，那里没有 composition，且用户从浏览器回来时
     * 可能停在任意页面。replay=1 保证「宿主尚未订阅就已 emit」不丢事件，
     * 代价是必须 [consumeActivated]。
     */
    private val _activated = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1)
    val activated: SharedFlow<Unit> = _activated.asSharedFlow()

    /** 收到即消费：清 replay 缓存，避免重建的收集者（旋转/主题切换）把同一次事件再弹一遍。 */
    fun consumeActivated() {
        _activated.resetReplayCache()
    }
}
