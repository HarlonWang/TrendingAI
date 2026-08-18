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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.back
import trendingai.shared.generated.resources.subscription_already_pro
import trendingai.shared.generated.resources.subscription_benefit_models
import trendingai.shared.generated.resources.subscription_benefit_models_free
import trendingai.shared.generated.resources.subscription_benefit_models_pro
import trendingai.shared.generated.resources.subscription_benefit_quota
import trendingai.shared.generated.resources.subscription_benefit_quota_free
import trendingai.shared.generated.resources.subscription_benefit_quota_pro
import trendingai.shared.generated.resources.subscription_checkout_failed
import trendingai.shared.generated.resources.subscription_col_free
import trendingai.shared.generated.resources.subscription_col_pro
import trendingai.shared.generated.resources.subscription_cta_signin
import trendingai.shared.generated.resources.subscription_cta_subscribe
import trendingai.shared.generated.resources.subscription_cta_view_price
import trendingai.shared.generated.resources.subscription_intro
import trendingai.shared.generated.resources.subscription_plan_annual
import trendingai.shared.generated.resources.subscription_pro_models_label
import trendingai.shared.generated.resources.subscription_plan_annual_unit
import trendingai.shared.generated.resources.subscription_plan_monthly
import trendingai.shared.generated.resources.subscription_plan_monthly_unit
import trendingai.shared.generated.resources.subscription_refund_note
import trendingai.shared.generated.resources.subscription_savings_badge
import trendingai.shared.generated.resources.subscription_title
import whl.trending.ai.auth.AuthState
import whl.trending.ai.auth.globalAuthManager
import whl.trending.ai.core.ProCheckout
import whl.trending.ai.core.platform.trackEvent
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.ui.common.TrendingScaffold
import whl.trending.ai.ui.common.TrendingTopAppBar

/**
 * 订阅页（付费墙本体）。触点是门，这一页是门后面的东西。
 *
 * 三条刻意为之的约束：
 *
 * 1. **不出现任何具体额度数字。** 额度是 credits 账本（对话 1 / 联网 3 / 深度调研 10），
 *    「每天 100 credits」对用户没有意义，而写死的「每天 N 条」在后端调额度的当天就变成谎话。
 *    与配额触顶卡、账户页用量卡同一条口径。
 *
 * 2. **不出现任何硬编码价格。** 价格由 `/api/billing/prices` 按访客所在地取——中国区是
 *    真·本地价（¥199）而不是 $39 的汇率换算，客户端猜不出来；拿不到就整页不报价，
 *    把定价交给收银台呈现，而不是显示一个可能不对的数字。
 *
 * 3. **不提 GitHub Sponsors。** 两条通道价格没对齐（Sponsors 明显更便宜且权益同档），
 *    并列展示等于把买家推去便宜那条。「想纯支持项目」的路径仍在关于页的捐赠入口里。
 *
 * 唯一给得出确切承诺的是模型：chip 直接渲染目录里 `minTier == pro` 的项，与模型选择器
 * 同一份数据源，后端调目录时本页自动跟随。
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

    val checkoutFailed = stringResource(Res.string.subscription_checkout_failed)
    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbarHostState.showSnackbar(checkoutFailed) }
    }

    // 曝光埋点：进页即记，与 checkout_opened 一起构成转化漏斗的两端
    LaunchedEffect(Unit) { trackEvent("paywall_view", mapOf("source" to ProCheckout.SOURCE_ACCOUNT)) }

    TrendingScaffold(
        topBar = {
            TrendingTopAppBar(
                title = { Text(stringResource(Res.string.subscription_title)) },
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
                stringResource(Res.string.subscription_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))
            BenefitTable(proModels = uiState.proModels)

            Spacer(Modifier.height(16.dp))
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
                    stringResource(Res.string.subscription_already_pro),
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
                            stringResource(
                                when {
                                    !loggedIn -> Res.string.subscription_cta_signin
                                    uiState.prices?.available == true -> Res.string.subscription_cta_subscribe
                                    else -> Res.string.subscription_cta_view_price
                                },
                            ),
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(Res.string.subscription_refund_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 权益对比：比「能做什么」而不是比数字。 */
@Composable
private fun BenefitTable(proModels: List<String>) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row {
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(Res.string.subscription_col_free),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(Res.string.subscription_col_pro),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
            }
            HorizontalDivider()
            BenefitRow(
                label = stringResource(Res.string.subscription_benefit_quota),
                free = stringResource(Res.string.subscription_benefit_quota_free),
                pro = stringResource(Res.string.subscription_benefit_quota_pro),
            )
            BenefitRow(
                label = stringResource(Res.string.subscription_benefit_models),
                free = stringResource(Res.string.subscription_benefit_models_free),
                pro = stringResource(Res.string.subscription_benefit_models_pro),
            )
            // 目录拉到了才展示具体型号——空目录时不留一行空白，也不猜任何名字。
            // 必须带「Pro 专属」前缀：chip 横跨整行、不落在任何一列下面，
            // 不标归属会被读成「免费也能用这些模型」，正好把意思说反（真机截图暴露）。
            if (proModels.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(Res.string.subscription_pro_models_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    proModels.take(3).forEach { name ->
                        SuggestionChip(onClick = {}, label = { Text(name) })
                    }
                }
            }
        }
    }
}

@Composable
private fun BenefitRow(label: String, free: String, pro: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            free,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            pro,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
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
