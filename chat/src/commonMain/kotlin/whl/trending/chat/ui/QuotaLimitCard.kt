package whl.trending.chat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import whl.trending.chat.host.chatHost
import trendingai.chat.generated.resources.Res
import trendingai.chat.generated.resources.chat_quota_auth_degraded
import trendingai.chat.generated.resources.chat_quota_exceeded
import trendingai.chat.generated.resources.chat_quota_login_cta
import trendingai.chat.generated.resources.chat_quota_pro_exceeded
import trendingai.chat.generated.resources.chat_quota_unlocked
import trendingai.chat.generated.resources.chat_quota_user_exceeded
import trendingai.chat.generated.resources.chat_retry
import whl.trending.chat.model.ChatError

/**
 * 个人配额触顶卡片（`quota_device`），按档位分形态：
 * - 匿名触顶：登录 CTA（转化点）
 * - 匿名触顶后完成登录：提示已解锁，给重试按钮直接续聊
 * - 登录触顶：纯提示，无 CTA
 *
 * 全局熔断（`quota_global`）不走本卡片，仍是普通错误文案——语义上与个人额度承诺切开。
 *
 * 登录档触顶不做 Pro 引导（2026-07-26 起）：撞墙时刻推销转化低、观感差，Pro 信息统一
 * 留在账户页由用户主动了解。匿名档的登录 CTA 保留——那是身份引导，不是付费引导。
 *
 * 文案刻意不写具体数字：后端配额是 credits 账本、各 feature 费率不同，
 * 一次深度调研就吃掉登录档一天的额度，任何写死的「每天 N 条」都会立刻变成谎话。
 */
@Composable
internal fun QuotaLimitCard(
    error: ChatError,
    onRetry: () -> Unit,
) {
    val loggedIn by chatHost.isLoggedIn.collectAsState(chatHost.isLoggedInNow())
    val isProTier = error.tier == ChatError.TIER_PRO
    val isUserTier = error.tier == ChatError.TIER_USER

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when {
            isProTier -> {
                // Pro 触顶（极罕见）：不透数字的软着陆，无 CTA（已是 Pro，明日恢复）
                QuotaText(Res.string.chat_quota_pro_exceeded)
            }
            isUserTier -> {
                // 登录档触顶：与 Pro 触顶同为纯提示，不推销、不外跳
                QuotaText(Res.string.chat_quota_user_exceeded)
            }
            loggedIn -> {
                if (error.authDegraded) {
                    // 发请求时就自认已登录、却被按匿名档处理（token 刷新失败/被拒）：
                    // 如实提示登录态未生效，不给「已解锁、重发即可」的死循环误导
                    QuotaText(Res.string.chat_quota_auth_degraded)
                } else {
                    // 匿名触顶后完成了登录：配额键已切换，重发即可继续
                    QuotaText(Res.string.chat_quota_unlocked)
                }
                TextButton(onClick = onRetry) {
                    Text(stringResource(Res.string.chat_retry))
                }
            }
            else -> {
                QuotaText(Res.string.chat_quota_exceeded)
                if (chatHost.canSignIn) {
                    Button(onClick = {
                        chatHost.signIn("chat_quota_card")
                    }) {
                        Text(stringResource(Res.string.chat_quota_login_cta))
                    }
                }
            }
        }
    }
}

@Composable
private fun QuotaText(resId: StringResource) {
    Text(
        text = stringResource(resId),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
