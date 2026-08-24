package whl.trending.ai.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Paddle 订阅相关的响应模型（后端 `src/api/billing.js`）。身份键是 `app_users.user_id`。 */

/** POST /api/billing/checkout 响应；[url] 必须在应用外打开，见 [whl.trending.ai.core.ProCheckout.openCheckout]。 */
@Serializable
data class CheckoutResponse(
    val url: String,
    @SerialName("transaction_id") val transactionId: String? = null,
)

/** GET /api/billing/subscription 响应；无订阅时 [subscription] 为 null（不是 404）。 */
@Serializable
data class SubscriptionResponse(
    val subscription: PaddleSubscription? = null,
)

/**
 * 一条 Paddle 订阅的落库快照，只服务「管理订阅」页展示。
 * 权益判定**不看这里**——那是 `/api/me` 的 `pro` 字段，别用 status 自行推权益。
 * @param scheduledChange Paddle 预约变更原文；客户端不解析内部结构，取消与否用非空判断。
 */
@Serializable
data class PaddleSubscription(
    @SerialName("paddle_subscription_id") val subscriptionId: String,
    /** Paddle 订阅状态：active / trialing / past_due / paused / canceled */
    val status: String,
    @SerialName("price_id") val priceId: String? = null,
    @SerialName("current_period_end") val currentPeriodEnd: String? = null,
    @SerialName("scheduled_change") val scheduledChange: String? = null,
) {
    /** 已约定周期末取消（仍可用到期末，UI 文案要与「立即失效」区分）。 */
    val cancelScheduled: Boolean get() = !scheduledChange.isNullOrBlank()
}

/** POST /api/billing/portal 响应：Paddle 客户门户深链。会话临时不可缓存，每次进管理页现取。 */
@Serializable
data class PortalResponse(
    val overview: String? = null,
    val cancel: String? = null,
    @SerialName("update_payment") val updatePayment: String? = null,
)

/**
 * GET /api/billing/prices 响应。客户端**不做任何价格计算与格式化**，全由服务端算好。
 * 字段全部可空：Paddle 超时或未配置时后端回 `{}`，订阅页降级为不报价。
 */
@Serializable
data class PricesResponse(
    val currency: String? = null,
    val country: String? = null,
    val annual: PriceView? = null,
    val monthly: PriceView? = null,
    /** 年付相对按月付满一年省下的百分比；不划算时为 null。 */
    @SerialName("annual_savings_percent") val savingsPercent: Int? = null,
) {
    /** 两档都拿到才算可展示。 */
    val available: Boolean get() = annual != null && monthly != null
}

/** 单档价格：[formatted] 已含货币符号，直接上屏。 */
@Serializable
data class PriceView(val formatted: String)
