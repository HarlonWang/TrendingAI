package whl.trending.ai.ui.subscription

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.back
import trendingai.shared.generated.resources.subscription_already_pro
import trendingai.shared.generated.resources.subscription_benefits_fallback
import trendingai.shared.generated.resources.subscription_checkout_failed
import trendingai.shared.generated.resources.subscription_cta_signin
import trendingai.shared.generated.resources.subscription_cta_subscribe
import trendingai.shared.generated.resources.subscription_cta_view_price
import trendingai.shared.generated.resources.subscription_intro
import trendingai.shared.generated.resources.subscription_plan_annual
import trendingai.shared.generated.resources.subscription_plan_annual_unit
import trendingai.shared.generated.resources.subscription_plan_monthly
import trendingai.shared.generated.resources.subscription_plan_monthly_unit
import trendingai.shared.generated.resources.subscription_refund_note
import trendingai.shared.generated.resources.subscription_savings_badge
import trendingai.shared.generated.resources.subscription_title
import wang.harlon.tinyui.HostCapability
import wang.harlon.tinyui.HostException
import wang.harlon.tinyui.HostServices
import wang.harlon.tinyui.InMemoryStore
import wang.harlon.tinyui.TinyUIPage
import whl.trending.ai.auth.AuthState
import whl.trending.ai.auth.globalAuthManager
import whl.trending.ai.core.ProCheckout
import whl.trending.ai.core.analytics.AppEvent
import whl.trending.ai.core.analytics.CheckoutStepKind
import whl.trending.ai.core.analytics.track
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.data.model.PricesResponse
import whl.trending.ai.data.repository.BillingRepository
import whl.trending.ai.tinyui.TinyUIHost
import whl.trending.ai.ui.common.TrendingScaffold
import whl.trending.ai.ui.common.TrendingTopAppBar

/**
 * 订阅页（付费墙本体）。触点是门，这一页是门后面的东西。
 *
 * 正文是 TinyUI 页 `pages/subscription`（源码在 ../trendingai-tinyui）：拉价、选档、下单流程都在 JS 里，
 * 这里只出壳（顶栏、Snackbar）和宿主能力。三条刻意为之的约束：
 *
 * 1. **文案以服务端为准。** 标题、副标题、权益行、CTA、退款说明、致谢、失败提示来自
 *    app-config 的 `pro_paywall`，本地 strings 只是从未拉到时的默认。约束见仓库 CLAUDE.md「订阅页文案」。
 *
 * 2. **不出现任何硬编码价格。** 价格由 `/api/billing/prices` 按访客所在地取——中国区是
 *    真·本地价（¥199）而不是 $39 的汇率换算，客户端猜不出来；拿不到就整页不报价，
 *    把定价交给收银台呈现，而不是显示一个可能不对的数字。
 *
 * 3. **不提 GitHub Sponsors。** 两条通道价格没对齐（Sponsors 明显更便宜且权益同档），
 *    并列展示等于把买家推去便宜那条。「想纯支持项目」的路径仍在关于页的捐赠入口里。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(
    onBack: () -> Unit,
    repository: BillingRepository = remember { BillingRepository() },
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val services = remember(repository) { subscriptionServices(repository, snackbarHostState) }
    LaunchedEffect(services) {
        globalAuthManager.authState.map { it is AuthState.LoggedIn }.collect { services.store.set(LOGGED_IN, "$it") }
    }
    LaunchedEffect(services) {
        globalSettingsManager.isPro.collect { services.store.set(IS_PRO, "$it") }
    }
    val propsJson = subscriptionProps()
    var page by remember { mutableStateOf<TinyUIHost.Page?>(null) }
    LaunchedEffect(Unit) { page = TinyUIHost.page(PAGE) }

    TrendingScaffold(
        topBar = {
            TrendingTopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } },
    ) { innerPadding ->
        page?.let {
            TinyUIPage(
                runtime = it.runtime,
                page = it.module,
                registry = TinyUIHost.registry,
                sink = TinyUIHost.sink,
                services = services,
                propsJson = propsJson,
                sourceMaps = it.maps,
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
            )
        }
    }
}

private const val PAGE = "pages/subscription"
private const val LOGGED_IN = "auth.loggedIn"
private const val IS_PRO = "pro.isPro"

/** 页面 props，字段与 ../trendingai-tinyui 的 `SubscriptionProps` 对应；文案先取远程再取本地默认，JS 拿到的都非空 */
@Serializable
private class SubscriptionProps(val content: Content, val plans: Map<String, PlanLabel>, val savingsBadge: String) {
    @Serializable
    class Content(
        val title: String,
        val subtitle: String,
        val benefits: List<BenefitItem>,
        val benefitsFallback: String,
        val ctaSubscribe: String,
        val ctaSignIn: String,
        val ctaViewPrice: String,
        val refundNote: String,
        val alreadyPro: String,
        val checkoutFailed: String,
    )

    @Serializable
    class PlanLabel(val title: String, val unit: String)
}

@Composable
private fun subscriptionProps(): String {
    val remote = resolvePaywallContent(globalSettingsManager.proPaywall(), globalSettingsManager.uiLanguage())
    val props = SubscriptionProps(
        content = SubscriptionProps.Content(
            title = remote.title ?: stringResource(Res.string.subscription_title),
            subtitle = remote.subtitle ?: stringResource(Res.string.subscription_intro),
            benefits = remote.benefits,
            benefitsFallback = stringResource(Res.string.subscription_benefits_fallback),
            ctaSubscribe = remote.ctaSubscribe ?: stringResource(Res.string.subscription_cta_subscribe),
            ctaSignIn = remote.ctaSignIn ?: stringResource(Res.string.subscription_cta_signin),
            ctaViewPrice = remote.ctaViewPrice ?: stringResource(Res.string.subscription_cta_view_price),
            refundNote = remote.refundNote ?: stringResource(Res.string.subscription_refund_note),
            alreadyPro = remote.alreadyPro ?: stringResource(Res.string.subscription_already_pro),
            checkoutFailed = remote.checkoutFailed ?: stringResource(Res.string.subscription_checkout_failed),
        ),
        plans = mapOf(
            ProCheckout.PLAN_ANNUAL to SubscriptionProps.PlanLabel(
                stringResource(Res.string.subscription_plan_annual),
                stringResource(Res.string.subscription_plan_annual_unit),
            ),
            ProCheckout.PLAN_MONTHLY to SubscriptionProps.PlanLabel(
                stringResource(Res.string.subscription_plan_monthly),
                stringResource(Res.string.subscription_plan_monthly_unit),
            ),
        ),
        // 百分比由 JS 从价格里填：资源串只能在组合期格式化，这里先放占位符
        savingsBadge = stringResource(Res.string.subscription_savings_badge, "{percent}"),
    )
    return remember(props) { Json.encodeToString(props) }
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

/** 名字与 ../trendingai-tinyui/src/host/index.ts 对应 */
private fun subscriptionServices(repository: BillingRepository, snackbar: SnackbarHostState): HostServices {
    val store = InMemoryStore().apply {
        set(LOGGED_IN, "${globalAuthManager.authState.value is AuthState.LoggedIn}")
        set(IS_PRO, "${globalSettingsManager.currentIsPro()}")
    }
    val json = Json { explicitNulls = true }
    fun String.arg(name: String): String? = Json.parseToJsonElement(this).jsonObject[name]?.jsonPrimitive?.content
    return HostServices(
        store = store,
        capabilities = mapOf(
            "billing.prices" to HostCapability { json.encodeToString(repository.fetchPrices()?.let(Prices::of) ?: Prices.NONE) },
            // 下单：创建交易 → 外跳收银台。权益以 webhook 为准，回前台由 ProCheckout.reconcile 对账，这里不等待、不轮询
            "checkout.start" to HostCapability { args ->
                val plan = args.arg("plan")?.takeIf { it == ProCheckout.PLAN_ANNUAL || it == ProCheckout.PLAN_MONTHLY }
                    ?: throw HostException("E_INVALID", "unknown plan")
                val checkout = repository.createCheckout(plan) ?: throw HostException("E_NET", "checkout not created")
                withContext(Dispatchers.Main) { ProCheckout.openCheckout(checkout.url, plan) }
                null
            },
            // 购买强制登录：身份键是 app_users.user_id，没有会话就无从把订阅挂到人身上
            "auth.signIn" to HostCapability { args ->
                withContext(Dispatchers.Main) { globalAuthManager.signIn(args.arg("source") ?: "paywall") }
                null
            },
            "analytics.checkoutStep" to HostCapability { args ->
                when (args.arg("kind")) {
                    "plan_selected" -> track(AppEvent.CheckoutStep(CheckoutStepKind.PLAN_SELECTED, plan = args.arg("plan")))
                    else -> throw HostException("E_INVALID", "unknown checkout step")
                }
                null
            },
            "ui.snackbar" to HostCapability { args ->
                args.arg("message")?.let { snackbar.showSnackbar(it) }
                null
            },
        ),
    )
}
