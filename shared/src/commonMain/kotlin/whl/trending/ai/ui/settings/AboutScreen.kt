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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.about
import trendingai.shared.generated.resources.account_link_sponsor_note
import trendingai.shared.generated.resources.app_name
import trendingai.shared.generated.resources.back
import trendingai.shared.generated.resources.confirm
import trendingai.shared.generated.resources.donate
import trendingai.shared.generated.resources.donate_github_desc
import trendingai.shared.generated.resources.donate_message
import trendingai.shared.generated.resources.privacy_policy
import trendingai.shared.generated.resources.tinyui_channel
import wang.harlon.appsettings.about.AboutContent
import wang.harlon.appsettings.about.AboutLink
import wang.harlon.appsettings.about.UpdateStatus
import whl.trending.ai.core.Constants
import whl.trending.ai.core.ProSponsor
import whl.trending.ai.core.analytics.AppEvent
import whl.trending.ai.core.analytics.SettingKey
import whl.trending.ai.core.analytics.track
import whl.trending.ai.core.platform.getAppVersion
import whl.trending.ai.core.platform.isIosPlatform
import whl.trending.ai.data.local.TINYUI_CHANNEL_PRODUCTION
import whl.trending.ai.data.local.TINYUI_CHANNEL_STAGING
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.tinyui.TinyUIUpdates
import whl.trending.ai.ui.common.TrendingScaffold
import whl.trending.ai.ui.common.TrendingTopAppBar
import whl.trending.ai.ui.home.githubLogoPainter
import whl.trending.ai.update.globalUpdateChecker

/**
 * 关于页：骨架与内置行在 appsettings-kmp 的 [AboutContent]，这里只做本 App 的事——
 * 更新检查怎么做（渠道相关）、链接在哪打开、埋哪些点、赞助弹窗与 TinyUI 调试通道。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onNavigateToWebPage: (url: String, title: String) -> Unit,
) {
    val isIos = isIosPlatform()
    val uriHandler = LocalUriHandler.current
    val appVersion = remember { getAppVersion() }
    val isChecking by globalUpdateChecker.isChecking.collectAsState()
    val isUpToDate by globalUpdateChecker.isUpToDate.collectAsState()
    val isCheckFailed by globalUpdateChecker.isCheckFailed.collectAsState()
    // iOS 的「检查更新」只是跳官网，没有状态可显示
    val updateStatus = when {
        isIos -> UpdateStatus.Idle
        isChecking -> UpdateStatus.Checking
        isUpToDate -> UpdateStatus.UpToDate
        isCheckFailed -> UpdateStatus.Failed
        else -> UpdateStatus.Idle
    }
    var showDonateDialog by remember { mutableStateOf(false) }
    // 隐藏开关：连点版本号 7 次出现 TinyUI 页面的热下发通道，用商店版验 staging；不在 production 时常显，便于切回。
    // 切换即按新通道下载，重启一次生效
    var versionTaps by remember { mutableIntStateOf(0) }
    val tinyUIChannel by globalSettingsManager.tinyUIChannel.collectAsState(globalSettingsManager.currentTinyUIChannel())
    val showTinyUIChannel = versionTaps >= 7 || tinyUIChannel != TINYUI_CHANNEL_PRODUCTION

    if (showDonateDialog) {
        DonateDialog(onDismiss = { showDonateDialog = false })
    }

    // 隐私政策标题要传给 WebView，须在 composable 作用域取
    val privacyTitle = stringResource(Res.string.privacy_policy)

    TrendingScaffold(
        topBar = {
            TrendingTopAppBar(
                title = { Text(stringResource(Res.string.about)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back))
                    }
                }
            )
        }
    ) { innerPadding ->
        AboutContent(
            name = stringResource(Res.string.app_name),
            version = appVersion,
            modifier = Modifier.padding(innerPadding),
            onVersionClick = { versionTaps++ },
            onLinkClick = { link ->
                when (link) {
                    is AboutLink.Changelog -> {
                        track(AppEvent.SettingsItemClicked(SettingKey.CHANGELOG))
                        uriHandler.openUri(link.url)
                    }
                    // 隐私政策在 App 内 WebView 打开，其余走系统浏览器
                    is AboutLink.Privacy -> onNavigateToWebPage(link.url, privacyTitle)
                    else -> uriHandler.openUri(link.url)
                }
            },
        ) {
            group {
                // 检查更新：apk 渠道点按自建检查，iOS 跳官网；play/fdroid 渠道由商店接管，不显示
                if (globalUpdateChecker.isEnabled || isIos) {
                    updateCheck(status = updateStatus) {
                        track(AppEvent.SettingsItemClicked(SettingKey.CHECK_UPDATE))
                        if (isIos) {
                            uriHandler.openUri(Constants.OFFICIAL_WEBSITE_URL)
                        } else {
                            globalUpdateChecker.manualCheck()
                        }
                    }
                }
                link(AboutLink.Changelog(Constants.CHANGELOG_URL))
                link(AboutLink.Website(Constants.OFFICIAL_WEBSITE_URL))
                item(
                    icon = Icons.Default.VolunteerActivism,
                    title = { Text(stringResource(Res.string.donate)) },
                    // 打开弹窗不埋点：赞助点击统一由 ProSponsor.openSponsorPage 报
                    // upsell_clicked(target=sponsor)，这里再报一条会让转化率分母翻倍
                    onClick = { showDonateDialog = true },
                )
                link(AboutLink.Privacy(Constants.PRIVACY_POLICY_URL))
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
