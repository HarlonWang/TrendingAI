package whl.trending.ai.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.upgrade_notice_action
import trendingai.shared.generated.resources.upgrade_notice_dismiss
import trendingai.shared.generated.resources.upgrade_notice_message
import trendingai.shared.generated.resources.upgrade_notice_title

/**
 * 「账号系统已升级」一次性提示横幅（落地第 4 步 C 方案，见 loginbase docs/plan.md）。
 *
 * **放首页而不是「我的」页**：「我的」页本身就展示未登录态，再加一张卡是纯噪音；
 * 真正需要解释的是「用户升级后在首页发现自己莫名未登录」，而他不会主动跑去账号页
 * 找原因。首页是唯一必然被看到的位置。
 *
 * **横幅而非 snackbar**：这条是解释性信息、需要读完，snackbar 几秒即逝，
 * 正在滑动的用户会直接错过——该困惑的人照样困惑。
 *
 * **也不是模态框**：升级后未登录是主动设计的行为而非故障，主信息流照常匿名可用，
 * 弹窗会打扰那些本来就没登录的用户。
 *
 * 关掉或点「去登录」都算看过，之后不再出现（[whl.trending.ai.core.UpgradeNotice.markShown]）。
 */
@Composable
fun UpgradeNoticeCard(
    onSignIn: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(Res.string.upgrade_notice_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(Res.string.upgrade_notice_message),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.upgrade_notice_dismiss))
                }
                TextButton(onClick = onSignIn) {
                    Text(stringResource(Res.string.upgrade_notice_action))
                }
            }
        }
    }
}
