package whl.trending.ai.ui.common

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.sponsor_link_needed_later
import trendingai.shared.generated.resources.sponsor_link_needed_message
import trendingai.shared.generated.resources.sponsor_link_needed_title
import whl.trending.ai.core.ProSponsor

/**
 * 全局「去过赞助页、但账户没关联 GitHub」提示宿主。
 *
 * 触发路径：用户从 GitHub Sponsors 页返回 → ON_RESUME 对账 → 后端回 `reason=github_not_linked`
 * （Pro 以 GitHub 数字 ID 发放，没有该身份就无从匹配）。
 *
 * **判定是启发式的，文案必须相应克制。** 「赞助了」这一半来自客户端本地的赞助页打开时间戳
 * （[ProSponsor.shouldReconcile]），是**意图**不是事实——后端没有 GitHub 身份可查，
 * 压根不知道这人付没付钱。点开赞助页看一眼就退出的用户同样会走到这里，所以文案只说
 * 「如果你刚完成赞助」，不能断言「赞助已收到」。真正的权威判定在关联完成之后的对账
 * （拿到 github_user_id → PAT 权威核对）。
 *
 * 放在 App 根部而非账户页：用户从浏览器回来时停在哪一页无法预期，只有根部宿主对所有返回
 * 路径统一生效。用弹窗而非 Snackbar——真付过钱的人值得一个明确交代，Snackbar 易被错过。
 */
@Composable
fun SponsorLinkHost() {
    var show by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        ProSponsor.needsGithubLink.collect {
            ProSponsor.consumeNeedsGithubLink()
            show = true
        }
    }

    if (!show) return

    LinkGithubDialog(
        title = stringResource(Res.string.sponsor_link_needed_title),
        message = stringResource(Res.string.sponsor_link_needed_message),
        onDismissRequest = { show = false },
        dismissButton = {
            TextButton(onClick = { show = false }) {
                Text(stringResource(Res.string.sponsor_link_needed_later))
            }
        },
    )
}
