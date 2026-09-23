package whl.trending.ai.tinyui

import app.tinyui.CapabilityRegistry
import app.tinyui.HostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import whl.trending.ai.auth.globalAuthManager
import whl.trending.ai.core.ProCheckout
import whl.trending.ai.core.analytics.AppEvent
import whl.trending.ai.core.analytics.CheckoutStepKind
import whl.trending.ai.core.analytics.track
import whl.trending.ai.data.model.PricesResponse
import whl.trending.ai.data.repository.BillingRepository

/** 名字与 ../trendingai-tinyui/src/host/index.ts 对应 */
internal fun capabilities(billing: Lazy<BillingRepository>): CapabilityRegistry = CapabilityRegistry().apply {
    val json = Json { explicitNulls = true }
    fun String.arg(name: String): String? = Json.parseToJsonElement(this).jsonObject[name]?.jsonPrimitive?.content

    register("billing.prices") { _, _ -> json.encodeToString(billing.value.fetchPrices()?.let(Prices::of) ?: Prices.NONE) }
    // 下单：创建交易 → 外跳收银台。权益以 webhook 为准，回前台由 ProCheckout.reconcile 对账，这里不等待、不轮询
    register("checkout.start") { args, _ ->
        val plan = args.arg("plan")?.takeIf { it == ProCheckout.PLAN_ANNUAL || it == ProCheckout.PLAN_MONTHLY }
            ?: throw HostException("E_INVALID", "unknown plan")
        val checkout = billing.value.createCheckout(plan) ?: throw HostException("E_NET", "checkout not created")
        withContext(Dispatchers.Main) { ProCheckout.openCheckout(checkout.url, plan) }
        null
    }
    // 购买强制登录：身份键是 app_users.user_id，没有会话就无从把订阅挂到人身上
    register("auth.signIn") { args, _ ->
        withContext(Dispatchers.Main) { globalAuthManager.signIn(args.arg("source") ?: "paywall") }
        null
    }
    register("analytics.checkoutStep") { args, _ ->
        when (args.arg("kind")) {
            "plan_selected" -> track(AppEvent.CheckoutStep(CheckoutStepKind.PLAN_SELECTED, plan = args.arg("plan")))
            else -> throw HostException("E_INVALID", "unknown checkout step")
        }
        null
    }
    register("ui.snackbar") { args, page ->
        val snackbar = page[PageSnackbar] ?: throw HostException("E_UNSUPPORTED", "no snackbar on ${page.name}")
        args.arg("message")?.let { snackbar.showSnackbar(it) }
        null
    }
}

/** `/api/billing/prices` 给 JS 的形态；两档缺一整页不报价 */
@Serializable
private class Prices(val available: Boolean, val annual: Price?, val monthly: Price?, val savingsPercent: Int?) {
    @Serializable
    class Price(val formatted: String)

    companion object {
        val NONE = Prices(available = false, annual = null, monthly = null, savingsPercent = null)

        fun of(r: PricesResponse) = Prices(
            available = r.available,
            annual = r.annual?.let { Price(it.formatted) },
            monthly = r.monthly?.let { Price(it.formatted) },
            savingsPercent = r.savingsPercent,
        )
    }
}
