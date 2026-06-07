package whl.trending.ai.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.whats_new_got_it
import trendingai.shared.generated.resources.whats_new_title
import whl.trending.ai.core.platform.getAppVersion
import whl.trending.ai.core.platform.getSystemLanguage
import whl.trending.ai.data.local.AppLanguage
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.update.WhatsNewInfo
import whl.trending.ai.update.parseWhatsNew
import whl.trending.ai.update.shouldShowWhatsNew

/**
 * 升级后首次启动弹一次「新版本更新说明」。
 * 内容来自随包内置的 composeResources/files/whatsnew.json（CI 打 tag 时生成）。
 */
@Composable
fun WhatsNewHost() {
    var info by remember { mutableStateOf<WhatsNewInfo?>(null) }
    val current = remember { getAppVersion() }

    LaunchedEffect(Unit) {
        val lastSeen = globalSettingsManager.getLastSeenWhatsNewVersion()
        if (lastSeen == null) {
            // 首次安装：静默记录当前版本，不弹
            globalSettingsManager.setLastSeenWhatsNewVersion(current)
            return@LaunchedEffect
        }
        if (lastSeen == current) return@LaunchedEffect

        val bundled = runCatching { Res.readBytes("files/whatsnew.json").decodeToString() }
            .getOrNull()?.let(::parseWhatsNew)
        if (bundled != null && !bundled.isEmpty &&
            shouldShowWhatsNew(current, bundled.version, lastSeen)
        ) {
            info = bundled
        } else {
            // dev 构建 / CI 生成失败等不满足弹出条件：直接标记已看，避免一直 pending
            globalSettingsManager.setLastSeenWhatsNewVersion(current)
        }
    }

    info?.let { whatsNew ->
        val appLanguage by globalSettingsManager.appLanguage
            .collectAsState(AppLanguage.FOLLOW_SYSTEM)
        val lang = appLanguage.isoCode ?: getSystemLanguage()
        val items = if (lang.startsWith("zh")) {
            whatsNew.zh.ifEmpty { whatsNew.en }
        } else {
            whatsNew.en.ifEmpty { whatsNew.zh }
        }
        WhatsNewDialog(
            version = whatsNew.version,
            items = items,
            onDismiss = {
                globalSettingsManager.setLastSeenWhatsNewVersion(current)
                info = null
            },
        )
    }
}

@Composable
private fun WhatsNewDialog(
    version: String,
    items: List<String>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(Res.string.whats_new_title, version))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items.forEach { item ->
                    Text("• $item")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.whats_new_got_it))
            }
        },
    )
}
