package whl.trending.ai.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.account_link_sponsor_note
import trendingai.shared.generated.resources.confirm
import trendingai.shared.generated.resources.donate
import trendingai.shared.generated.resources.donate_github_desc
import trendingai.shared.generated.resources.donate_message
import trendingai.shared.generated.resources.tinyui_channel
import wang.harlon.appsettings.AboutScreen
import wang.harlon.appsettings.about.UpdateStatus
import whl.trending.ai.core.ProSponsor
import whl.trending.ai.data.local.TINYUI_CHANNEL_PRODUCTION
import whl.trending.ai.data.local.TINYUI_CHANNEL_STAGING
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.tinyui.TinyUIUpdates
import whl.trending.ai.ui.home.githubLogoPainter
import whl.trending.ai.update.globalUpdateChecker

/**
 * 关于页：骨架、头部、链接与检查更新行来自 appsettings-kmp（数据在 App.kt 的 rememberAppSettings 里配），
 * 这里只追加本 App 私有的两行——赞助弹窗与 TinyUI 调试通道（连点版本号 7 次出现；不在 production 时常显，便于切回）。
 */
@Composable
fun TrendingAboutScreen(onBack: () -> Unit) {
    var showDonateDialog by remember { mutableStateOf(false) }
    var versionTaps by remember { mutableIntStateOf(0) }
    val tinyUIChannel by globalSettingsManager.tinyUIChannel.collectAsState(globalSettingsManager.currentTinyUIChannel())
    val showTinyUIChannel = versionTaps >= 7 || tinyUIChannel != TINYUI_CHANNEL_PRODUCTION

    if (showDonateDialog) {
        DonateDialog(onDismiss = { showDonateDialog = false })
    }

    AboutScreen(onBack = onBack, onVersionClick = { versionTaps++ }) {
        item(
            icon = Icons.Default.VolunteerActivism,
            title = { Text(stringResource(Res.string.donate)) },
            // 打开弹窗不埋点：赞助点击统一由 ProSponsor.openSponsorPage 报
            // upsell_clicked(target=sponsor)，这里再报一条会让转化率分母翻倍
            onClick = { showDonateDialog = true },
        )
        if (showTinyUIChannel) {
            item(
                icon = Icons.Default.Science,
                title = { Text(stringResource(Res.string.tinyui_channel)) },
                trailing = { Text(tinyUIChannel) },
                onClick = {
                    globalSettingsManager.setTinyUIChannel(
                        if (tinyUIChannel == TINYUI_CHANNEL_STAGING) TINYUI_CHANNEL_PRODUCTION else TINYUI_CHANNEL_STAGING,
                    )
                    TinyUIUpdates.checkNow()
                },
            )
        }
    }
}

/** 「检查更新」行的显示状态：iOS 只是跳官网，没有状态可显示。 */
@Composable
fun trendingUpdateStatus(isIos: Boolean): UpdateStatus {
    val isChecking by globalUpdateChecker.isChecking.collectAsState()
    val isUpToDate by globalUpdateChecker.isUpToDate.collectAsState()
    val isCheckFailed by globalUpdateChecker.isCheckFailed.collectAsState()
    return when {
        isIos -> UpdateStatus.Idle
        isChecking -> UpdateStatus.Checking
        isUpToDate -> UpdateStatus.UpToDate
        isCheckFailed -> UpdateStatus.Failed
        else -> UpdateStatus.Idle
    }
}

@Composable
private fun DonateDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.donate)) },
        text = {
            Column {
                Text(stringResource(Res.string.donate_message))
                Spacer(Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { ProSponsor.openSponsorPage(ProSponsor.SOURCE_SETTINGS_DONATE) }
                        .padding(vertical = 12.dp)
                ) {
                    Icon(
                        painter = githubLogoPainter(),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.padding(start = 16.dp)) {
                        Text("GitHub Sponsors", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = stringResource(Res.string.donate_github_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // 权益前置条件写在入口处，而不是等赞助完再补救：赞助后的即时引导只覆盖
                        // 「30 分钟内回到 app」的路径，隔天才打开、或换设备赞助的人接不到。
                        Text(
                            text = stringResource(Res.string.account_link_sponsor_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.confirm))
            }
        }
    )
}
