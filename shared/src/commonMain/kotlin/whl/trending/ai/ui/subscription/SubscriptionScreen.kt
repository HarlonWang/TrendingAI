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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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
import app.tinyui.HostServices
import app.tinyui.InMemoryStore
import app.tinyui.updates.UpdatesPage
import whl.trending.ai.tinyui.PageSnackbar
import whl.trending.ai.tinyui.TinyUIUpdates
import whl.trending.ai.tinyui.TrendingTinyUI
import whl.trending.ai.auth.AuthState
import whl.trending.ai.auth.globalAuthManager
import whl.trending.ai.core.ProCheckout
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.ui.common.TrendingScaffold
import whl.trending.ai.ui.common.TrendingTopAppBar

/**
 * 订阅页（付费墙本体）。触点是门，这一页是门后面的东西。
 *
 * 正文是 TinyUI 页 `trendingai/subscription`（源码在 ../trendingai-tinyui，可热下发）：拉价、选档、下单流程都在 JS 里，
 * 这里只出壳（顶栏、Snackbar）；宿主能力在 tinyui/Capabilities.kt。三条刻意为之的约束：
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
fun SubscriptionScreen(onBack: () -> Unit) {
    val snackbarHostState = remember { SnackbarHostState() }
    val services = remember { subscriptionServices(snackbarHostState) }
    LaunchedEffect(services) {
        globalAuthManager.authState.map { it is AuthState.LoggedIn }.collect { services.store.set(LOGGED_IN, "$it") }
    }
    LaunchedEffect(services) {
        globalSettingsManager.isPro.collect { services.store.set(IS_PRO, "$it") }
    }
    val propsJson = subscriptionProps()
    // App 启动时已建好（TinyUIUpdates.start），这里通常一进来就有
    val updates by TinyUIUpdates.updates.collectAsState()

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
        updates?.let {
            UpdatesPage(
                updates = it,
                name = PAGE,
                host = TrendingTinyUI.host,
                services = services,
                propsJson = propsJson,
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
            )
        }
    }
}

private const val PAGE = "${TinyUIUpdates.PKG}/subscription"
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

/** 能力在 App 级注册（tinyui/Capabilities.kt），这里只给本页的 store 与 snackbar */
private fun subscriptionServices(snackbar: SnackbarHostState): HostServices = HostServices(
    store = InMemoryStore().apply {
        set(LOGGED_IN, "${globalAuthManager.authState.value is AuthState.LoggedIn}")
        set(IS_PRO, "${globalSettingsManager.currentIsPro()}")
    },
    locals = listOf(PageSnackbar provides snackbar),
)
