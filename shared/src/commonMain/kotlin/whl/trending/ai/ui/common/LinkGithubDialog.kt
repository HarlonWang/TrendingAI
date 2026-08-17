package whl.trending.ai.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.account_link_github
import whl.trending.ai.core.AccountLink

/**
 * 「去关联 GitHub」弹窗的共用外壳。
 *
 * 两个触发点的时机与文案不同——「升级前发现没关联」（账户页）与「已赞助但没关联」
 * （`SponsorLinkHost`）——但主动作完全一样：跳关联页、来源都记 `SOURCE_UPGRADE_DIALOG`。
 * 此前两处各写一份 `AlertDialog`，改一处容易漏另一处。
 *
 * 次按钮差异大（一处是「仍然赞助」并跳赞助页，一处是「稍后」纯关闭），故做成槽位由调用方给。
 */
@Composable
fun LinkGithubDialog(
    title: String,
    message: String,
    onDismissRequest: () -> Unit,
    dismissButton: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = {
                onDismissRequest()
                // 关联成功后 OAuthOutcomeHost 会自动刷身份 + 补一次 pro/refresh，
                // 用户不需要再回赞助页，也不需要重启 app。
                AccountLink.openLinkGithubPage(AccountLink.SOURCE_UPGRADE_DIALOG)
            }) {
                Text(stringResource(Res.string.account_link_github))
            }
        },
        dismissButton = dismissButton,
    )
}
