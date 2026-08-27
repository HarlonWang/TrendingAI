package whl.trending.ai.data.repository

import kotlinx.coroutines.CancellationException
import whl.trending.ai.core.analytics.AppEvent
import whl.trending.ai.core.analytics.track
import whl.trending.ai.data.model.CheckoutResponse
import whl.trending.ai.data.model.PaddleSubscription
import whl.trending.ai.data.model.PortalResponse
import whl.trending.ai.data.model.PricesResponse
import whl.trending.ai.data.remote.ApiException
import whl.trending.ai.data.remote.TrendingApi

/**
 * Paddle 订阅数据源（后端 `src/api/billing.js`）。
 * 失败一律返回 null 而不抛（UI 要的是降级形态），但 CancellationException 必须原样抛出——
 * 吞掉会把「协程被取消」当成「请求失败」，取消再也传不下去。
 */
open class BillingRepository(
    private val api: TrendingApi = TrendingApi(),
) {

    /**
     * 两档价格。公开接口、不带 token——定价页在登录之前就要看得见。
     * 服务端拿不到 Paddle 时回 `{}`（各字段为 null），照样是成功响应，交由 UI 降级。
     */
    open suspend fun fetchPrices(): PricesResponse? = try {
        api.fetchPrices()
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        track(AppEvent.ApiFailed("billing/prices", statusOf(e)))
        null
    }

    /** 创建交易并拿收银台地址。[plan] 取 `"annual"` / `"monthly"`，约束见 [TrendingApi.createCheckout]。 */
    open suspend fun createCheckout(plan: String): CheckoutResponse? = try {
        api.createCheckout(plan)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        track(AppEvent.ApiFailed("billing/checkout", statusOf(e)))
        null
    }

    /** 当前订阅快照；无订阅时后端回 `{subscription: null}`，与请求失败（null）不同形。 */
    open suspend fun fetchSubscription(): PaddleSubscription? = try {
        api.fetchSubscription().subscription
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        track(AppEvent.ApiFailed("billing/subscription", statusOf(e)))
        null
    }

    /**
     * Paddle 客户门户深链（取消订阅 / 更换支付方式）。会话临时、不可缓存，每次现取。
     * 无订阅记录时后端回 404——不额外区分，UI 统一按「暂时打不开」处理即可。
     */
    open suspend fun createPortal(): PortalResponse? = try {
        api.createPortalSession()
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        track(AppEvent.ApiFailed("billing/portal", statusOf(e)))
        null
    }

    private fun statusOf(e: Exception): Int = (e as? ApiException)?.statusCode ?: -1
}
