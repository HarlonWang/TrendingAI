package whl.trending.ai.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Paddle 订阅相关的响应模型（后端 `src/api/billing.js`，设计见调研 D.3 / D.9）。
 *
 * 三个接口都只认 loginbase 轨的 token——身份键是 `app_users.user_id`，Logto 轨一律 401。
 * 这不是兼容遗漏而是后端刻意收窄的契约，客户端不必为 Logto 轨做任何回退。
 */

/**
 * POST /api/billing/checkout 响应：Paddle 收银台地址。
 *
 * [url] 指向官网 `/pay?_ptxn=txn_xxx`，**必须在应用外打开**（Custom Tab / 系统浏览器）：
 * 收银台要走 Paddle 自己的三方支付跳转与风控，内置 WebView 里跑不通；且外跳才能保证
 * 用户付完回来必然触发 ON_RESUME，[whl.trending.ai.core.ProCheckout] 的回流对账才有机会执行。
 */
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
 * 一条 Paddle 订阅的落库快照（webhook 写入，客户端只读）。
 *
 * 权益判定**不看这里**——那是 `/api/me` 的 `pro` 字段（后端 `isProUser` 一条 SQL 取或，
 * 同时覆盖 Sponsors 与 Paddle 两路）。本模型只服务于「管理订阅」页面的展示：
 * 到期时间、是否已约定周期末取消。两处口径分开是有意的，别用 status 自行推权益。
 *
 * @param scheduledChange Paddle 的预约变更原文（JSON 字符串），非空通常意味着「已约定周期末取消」。
 *   客户端不解析其内部结构——Paddle 可能扩展字段，取消与否用非空判断足够。
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
    /** 是否已约定在本周期结束时取消（仍可用到期末，UI 文案要与「立即失效」区分）。 */
    val cancelScheduled: Boolean get() = !scheduledChange.isNullOrBlank()
}

/**
 * POST /api/billing/portal 响应：Paddle 客户门户深链。
 *
 * 会话是临时的、不可缓存，每次进管理页现取（后端 D.3.5）。取消订阅界面完全由 Paddle 承担，
 * 客户端不自建——这同时满足支付宝/微信「便捷解约」的合规要求。
 */
@Serializable
data class PortalResponse(
    val overview: String? = null,
    val cancel: String? = null,
    @SerialName("update_payment") val updatePayment: String? = null,
)

/**
 * GET /api/billing/prices 响应：按访客所在地算好的两档价格。
 *
 * 客户端**不做任何价格计算与格式化**——[formatted] 是 Paddle 按该地区规则格式化好的
 * 字符串（中国区「199.00 元」、美国「$39.00」），[savingsPercent] 也由服务端算。
 * 这与额度文案「不写死数字」是同一条原则：价格是后端说了算的东西，写进客户端就会说谎，
 * 而且中国区是真·本地价（¥199）不是 $39 的汇率换算，客户端根本猜不出来。
 *
 * 三个字段**全部可空**：Paddle 超时或未配置时后端回 `{}`，此时订阅页降级为不报价，
 * 只留「查看价格」入口把定价交给收银台呈现——购买链路不依赖价格展示。
 */
@Serializable
data class PricesResponse(
    val currency: String? = null,
    val country: String? = null,
    val annual: PriceView? = null,
    val monthly: PriceView? = null,
    /** 年付相对「按月付满一年」省下的百分比；服务端向下取整，不划算时为 null。 */
    @SerialName("annual_savings_percent") val savingsPercent: Int? = null,
) {
    /** 两档都拿到才算可展示——只报一半价格比不报更让人犯嘀咕。 */
    val available: Boolean get() = annual != null && monthly != null
}

/** 单档价格：[formatted] 已含货币符号，直接上屏。 */
@Serializable
data class PriceView(val formatted: String)
