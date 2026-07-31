package whl.trending.ai.ui.common

import androidx.compose.material3.AlertDialog
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
import trendingai.shared.generated.resources.account_link_github
import trendingai.shared.generated.resources.sponsor_link_needed_later
import trendingai.shared.generated.resources.sponsor_link_needed_message
import trendingai.shared.generated.resources.sponsor_link_needed_title
import whl.trending.ai.core.AccountLink
import whl.trending.ai.core.ProSponsor

/**
 * 全局「赞助已到账但账户没关联 GitHub」提示宿主。
 *
 * 触发路径：用户从 GitHub Sponsors 页返回 → ON_RESUME 对账 → 后端回
 * `reason=github_not_linked`（钱付了，但 Pro 以 GitHub 数字 ID 发放，对不上人）。
 *
 * 这是 2026-07-29 首位赞助者付完钱被当免费用户拦 48 分钟的修复面：在此之前对账
 * 只拿得到裸 `pro:false`，与「确实没赞助」同形，客户端只能沉默。
 *
 * 放在 App 根部而非账户页：用户从浏览器回来时停在哪一页无法预期，只有根部宿主对所有
 * 返回路径统一生效。用弹窗而非 Snackbar——刚付过钱的人值得一个明确的交代，Snackbar 易被错过。
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

    AlertDialog(
        onDismissRequest = { show = false },
        title = { Text(stringResource(Res.string.sponsor_link_needed_title)) },
        text = { Text(stringResource(Res.string.sponsor_link_needed_message)) },
        confirmButton = {
            TextButton(onClick = {
                show = false
                // 关联成功后，MainActivity 的 AccountLink 分支会自动补一次 pro/refresh，
                // 用户不需要再回赞助页，也不需要重启 app。
                AccountLink.openLinkGithubPage(AccountLink.SOURCE_UPGRADE_DIALOG)
            }) {
                Text(stringResource(Res.string.account_link_github))
            }
        },
        dismissButton = {
            TextButton(onClick = { show = false }) {
                Text(stringResource(Res.string.sponsor_link_needed_later))
            }
        },
    )
}
