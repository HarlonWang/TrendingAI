package whl.trending.chat.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import trendingai.chat.generated.resources.Res
import trendingai.chat.generated.resources.chat_model_unlock_dismiss
import trendingai.chat.generated.resources.chat_upgrade_pro
import whl.trending.chat.host.chatHost

/**
 * Pro 门槛告知弹窗：说明功能仅 Pro 可用、免费路径仍在。「升级 Pro」关闭弹窗并打开宿主订阅页，
 * 「知道了」只关闭弹窗；不拦截用户正在做的事。
 */
@Composable
internal fun ProGateDialog(
    title: String,
    message: String,
    paywallSource: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                chatHost.openPaywall(paywallSource)
            }) {
                Text(stringResource(Res.string.chat_upgrade_pro))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.chat_model_unlock_dismiss))
            }
        },
    )
}
