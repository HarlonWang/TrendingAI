package whl.trending.updater

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import whl.trending.ai.core.Constants
import whl.trending.ai.core.platform.getSystemLanguage
import whl.trending.ai.core.platform.openDownloadUrl
import whl.trending.ai.core.platform.openUrl
import whl.trending.ai.data.local.AppLanguage
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.update.pickByLanguage

@Composable
fun UpdateDialog(updateInfo: UpdateInfo, downloadUrl: String, onDismiss: () -> Unit) {
    val appLanguage by globalSettingsManager.appLanguage
        .collectAsState(AppLanguage.FOLLOW_SYSTEM)
    val lang = appLanguage.isoCode ?: getSystemLanguage()
    val items = updateInfo.whatsNew?.let { pickByLanguage(lang, it.zh, it.en) }.orEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.update_dialog_title, updateInfo.latestVersion))
        },
        text = {
            if (items.isEmpty()) {
                Text(stringResource(R.string.update_dialog_message, updateInfo.currentVersion))
            } else {
                // 样式对齐升级首启的 WhatsNewDialog：逐条 • 列表、可滚动
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items.forEach { item ->
                        Text("• $item")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // APK 直链走系统浏览器接管下载，不进 Custom Tab（见 openDownloadUrl）
                openDownloadUrl(downloadUrl)
                onDismiss()
            }) {
                Text(stringResource(R.string.update_dialog_download))
            }
        },
        dismissButton = {
            Row {
                // 更新内容已展示在弹窗内时不再提供跳转，避免三按钮拥挤
                if (items.isEmpty()) {
                    TextButton(onClick = {
                        openUrl(Constants.RELEASES_URL)
                        onDismiss()
                    }) {
                        Text(stringResource(R.string.update_dialog_changelog))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.update_dialog_cancel))
                }
            }
        }
    )
}
