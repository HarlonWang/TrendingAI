package whl.trending.ai.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.checkout_activated_message
import trendingai.shared.generated.resources.checkout_activated_title
import whl.trending.ai.core.ProCheckout

/**
 * 「Pro 已开通」提示宿主。
 *
 * 触发路径：付完款从收银台返回 → ON_RESUME 对账（[ProCheckout.reconcile]）拿到 pro=true。
 *
 * 与 [SponsorLinkHost] 的区别在于**它只在确定成功时开口**：Paddle 的身份键就是
 * `app_users.user_id`，不存在 Sponsors 那种「付了钱但账号对不上」的启发式判断，
 * 权益到没到账是后端 D1 的事实。对账没拿到 pro 时一律沉默——那多半只是用户
 * 没走完付款，此刻说任何话都可能是错的。
 *
 * 放在 App 根部而非订阅页：用户从浏览器回来时停在哪一页无法预期，且付款期间
 * 订阅页的 NavEntry 完全可能已被销毁。
 */
@Composable
fun CheckoutResultHost() {
    var show by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        ProCheckout.activated.collect {
            ProCheckout.consumeActivated()
            show = true
        }
    }

    if (!show) return

    InfoDialog(
        title = stringResource(Res.string.checkout_activated_title),
        content = stringResource(Res.string.checkout_activated_message),
        onDismiss = { show = false },
    )
}
