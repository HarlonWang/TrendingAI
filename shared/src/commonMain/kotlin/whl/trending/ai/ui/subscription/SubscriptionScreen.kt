package whl.trending.ai.ui.subscription

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
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
import whl.trending.ai.auth.AuthState
import whl.trending.ai.auth.globalAuthManager
import whl.trending.ai.core.ProCheckout
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.ui.common.TrendingScaffold
import whl.trending.ai.ui.common.TrendingTopAppBar

/**
 * 订阅页（付费墙本体）。触点是门，这一页是门后面的东西。
 *
 * 三条刻意为之的约束：
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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SubscriptionScreen(
    onBack: () -> Unit,
    viewModel: SubscriptionViewModel = viewModel { SubscriptionViewModel() },
) {
    val uiState by viewModel.uiState.collectAsState()
    val authState by globalAuthManager.authState.collectAsState()
    val isPro by globalSettingsManager.isPro.collectAsState(
        initial = globalSettingsManager.currentIsPro(),
    )
    val snackbarHostState = remember { SnackbarHostState() }
    val loggedIn = authState is AuthState.LoggedIn

    val checkoutFailed = uiState.copy.checkoutFailed ?: stringResource(Res.string.subscription_checkout_failed)
    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbarHostState.showSnackbar(checkoutFailed) }
    }

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
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                uiState.copy.title ?: stringResource(Res.string.subscription_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                uiState.copy.subtitle ?: stringResource(Res.string.subscription_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(24.dp))
            BenefitList(items = uiState.copy.benefits)

            Spacer(Modifier.height(24.dp))
            if (uiState.loading) {
                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), Alignment.Center) {
                    LoadingIndicator(modifier = Modifier.size(24.dp))
                }
            } else {
                PlanOptions(
                    state = uiState,
                    onSelect = viewModel::selectPlan,
                )
            }

            Spacer(Modifier.height(16.dp))
            when {
                // 直接进来的 Pro 用户（账户页不会给入口，但深链/返回栈可能到这）：不推销
                isPro -> Text(
                    uiState.copy.alreadyPro ?: stringResource(Res.string.subscription_already_pro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                else -> Button(
                    onClick = {
                        if (loggedIn) viewModel.startCheckout()
                        // 购买强制登录：身份键是 app_users.user_id，没有会话就无从把订阅挂到人身上
                        else globalAuthManager.signIn("paywall")
                    },
                    enabled = !uiState.checkingOut,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (uiState.checkingOut) {
                        LoadingIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(
                            when {
                                !loggedIn -> uiState.copy.ctaSignIn
                                    ?: stringResource(Res.string.subscription_cta_signin)
                                uiState.prices?.available == true -> uiState.copy.ctaSubscribe
                                    ?: stringResource(Res.string.subscription_cta_subscribe)
                                else -> uiState.copy.ctaViewPrice
                                    ?: stringResource(Res.string.subscription_cta_view_price)
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                uiState.copy.refundNote ?: stringResource(Res.string.subscription_refund_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 权益清单：只列 Pro 得到什么，不做免费/Pro 对比列 */
@Composable
private fun BenefitList(items: List<BenefitItem>) {
    if (items.isEmpty()) {
        Text(
            stringResource(Res.string.subscription_benefits_fallback),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        items.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    benefitIcon(item.icon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(16.dp))
                Text(item.text, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** key 与后端 lib/pro-benefits.js 对应；认不出的 key 用通用勾选，新行不必等客户端发版 */
private fun benefitIcon(key: String?): ImageVector = when (key) {
    "quota" -> Icons.Outlined.Bolt
    "models" -> Icons.Outlined.AutoAwesome
    "voice" -> Icons.Outlined.Mic
    else -> Icons.Outlined.CheckCircle
}

/**
 * 双档选择。年付默认选中并带「省 x%」角标——这是「主推年付」的**全部**体现；
 * 月付同屏并列、不折叠、不需要展开才看得见（定价拍板的硬约束）。
 * 省下的百分比由服务端按地区算（中国区 43%、美国 35%），客户端不做算术。
 */
@Composable
private fun PlanOptions(
    state: SubscriptionUiState,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PlanCard(
            title = stringResource(Res.string.subscription_plan_annual),
            unit = stringResource(Res.string.subscription_plan_annual_unit),
            price = state.prices?.annual?.formatted,
            // 百分号在这里拼而不写进资源串：CMP 的 stringResource 不做 printf 的
            // `%%` → `%` 转义，资源里写 %% 会原样显示成「省 42%%」（真机实测）
            badge = state.prices?.savingsPercent
                ?.let { stringResource(Res.string.subscription_savings_badge, "$it%") },
            selected = state.selectedPlan == ProCheckout.PLAN_ANNUAL,
            onClick = { onSelect(ProCheckout.PLAN_ANNUAL) },
        )
        PlanCard(
            title = stringResource(Res.string.subscription_plan_monthly),
            unit = stringResource(Res.string.subscription_plan_monthly_unit),
            price = state.prices?.monthly?.formatted,
            badge = null,
            selected = state.selectedPlan == ProCheckout.PLAN_MONTHLY,
            onClick = { onSelect(ProCheckout.PLAN_MONTHLY) },
        )
    }
}

@Composable
private fun PlanCard(
    title: String,
    unit: String,
    price: String?,
    badge: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    if (badge != null) {
                        Spacer(Modifier.size(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(
                                badge,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                // 价格取不到时整行不显示——宁可不报价，也不显示一个可能不对的数字
                if (price != null) {
                    Text(
                        "$price · $unit",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
