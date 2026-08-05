package whl.trending.ai.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.confirm

/**
 * 纯说明弹窗：标题 + 正文 + 一个「知道了」。
 *
 * 原先是 `TrendingScreen` 的私有组件，别处要弹说明只能手写 `AlertDialog`。按
 * `docs/interaction-consistency-audit.md` 的决策表，说明 / 引导类一律走这里。
 * 需要第二个动作的说明弹窗（如设置页的摘要语言）不适用——那属于「说明 + 多动作」，
 * 仍自行组装 `AlertDialog`。
 */
@Composable
fun InfoDialog(
    title: String,
    content: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(content) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.confirm))
            }
        },
    )
}
