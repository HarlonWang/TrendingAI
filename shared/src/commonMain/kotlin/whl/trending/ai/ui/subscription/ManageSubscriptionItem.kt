package whl.trending.ai.ui.subscription

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.account_manage_subscription
import trendingai.shared.generated.resources.account_manage_subscription_hint
import trendingai.shared.generated.resources.subscription_cancel_scheduled
import trendingai.shared.generated.resources.subscription_period_end
import trendingai.shared.generated.resources.subscription_portal_failed
import trendingai.shared.generated.resources.subscription_title
import whl.trending.ai.core.DateTimeUtils
import whl.trending.ai.core.platform.openUrl
import whl.trending.ai.core.platform.trackEvent
import whl.trending.ai.data.model.PaddleSubscription
import whl.trending.ai.data.repository.BillingRepository
import whl.trending.ai.ui.common.InfoDialog
import whl.trending.ai.ui.common.SettingsGroup

/**
 * 账户页的「管理订阅」行。取消订阅本身完全由 Paddle 客户门户承担，这里只做跳转入口——
 * 门户深链同时满足支付宝/微信「便捷解约」的合规要求，自建取消界面既多余又更难合规。
 *
 * 数据与整页解耦（同 `PlanUsageCard` 的做法）：自己拉 `/api/billing/subscription`，
 * 失败或无订阅时**整行不显示**。于是它天然只对 Paddle 订阅者出现：
 * Sponsors 那条通道的 Pro 用户查不到订阅记录，他们的续费本就在 GitHub 上管，
 * 不该在这里给一个点进去是别人家页面的入口。
 *
 * 门户会话是临时的、不可缓存，所以在点击时才现取，而不是随页面加载预取。
 */
@Composable
fun ManageSubscriptionItem(
    isPro: Boolean,
    repository: BillingRepository = remember { BillingRepository() },
) {
    var subscription by remember { mutableStateOf<PaddleSubscription?>(null) }
    var opening by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 只有 Pro 才查：免费用户不可能有生效订阅，没必要为每次进账户页多打一个请求
    LaunchedEffect(isPro) {
        subscription = if (isPro) repository.fetchSubscription() else null
    }

    val sub = subscription ?: return

    if (failed) {
        InfoDialog(
            title = stringResource(Res.string.subscription_title),
            content = stringResource(Res.string.subscription_portal_failed),
            onDismiss = { failed = false },
        )
    }

    // 与账户页其余入口（GithubEntryCard / LinkGithubCard）同一套卡片语言：
    // 这一行就排在它们旁边，用裸 ListItem 会在同一页出现两种卡片形状与间距。
    SettingsGroup(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        settingsItem(
            title = { Text(stringResource(Res.string.account_manage_subscription)) },
            description = { Text(supportingTextOf(sub)) },
            trailing = {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            },
            enabled = !opening,
            onClick = {
                opening = true
                trackEvent("manage_subscription_click")
                scope.launch {
                    // finally 而不是顺序赋值：请求被取消时（协程抛 CancellationException）
                    // 也要放开点击态。当前 opening 与 scope 同属一个 composition、一起销毁，
                    // 卡死不会真的发生；但一旦有人把它改成 rememberSaveable、或换成生命周期
                    // 更长的 scope，这一行就是「整行永久点不动」与否的分界。
                    try {
                        val portal = repository.createPortal()
                        // 优先落在取消页：点进「管理订阅」的人多数是来退订的，少一跳
                        val url = portal?.cancel ?: portal?.overview
                        if (url != null) openUrl(url) else failed = true
                    } finally {
                        opening = false
                    }
                }
            },
        )
    }
}

/**
 * 副标题：已约定周期末取消时如实说明，否则报下次续费日期。
 * 两者必须分开说——「取消」（停续费、用到期末）与「立即失效」是完全不同的承诺。
 */
@Composable
private fun supportingTextOf(sub: PaddleSubscription): String = when {
    sub.cancelScheduled -> stringResource(Res.string.subscription_cancel_scheduled)
    sub.currentPeriodEnd != null ->
        stringResource(
            Res.string.subscription_period_end,
            DateTimeUtils.formatToLocalTime(sub.currentPeriodEnd).take(10),
        )
    else -> stringResource(Res.string.account_manage_subscription_hint)
}
