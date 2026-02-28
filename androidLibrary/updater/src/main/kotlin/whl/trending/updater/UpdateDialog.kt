package whl.trending.updater

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import whl.trending.ai.core.Constants
import whl.trending.ai.core.platform.openUrl

@Composable
fun UpdateDialog(updateInfo: UpdateInfo, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.update_dialog_title, updateInfo.latestVersion))
        },
        text = {
            Text(stringResource(R.string.update_dialog_message, updateInfo.currentVersion))
        },
        confirmButton = {
            TextButton(onClick = {
                openUrl(Constants.OFFICIAL_WEBSITE_URL)
                onDismiss()
            }) {
                Text(stringResource(R.string.update_dialog_download))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    openUrl(Constants.RELEASES_URL)
                    onDismiss()
                }) {
                    Text(stringResource(R.string.update_dialog_changelog))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.update_dialog_cancel))
                }
            }
        }
    )
}
