package whl.trending.ai.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.force_update_button
import trendingai.shared.generated.resources.force_update_message
import trendingai.shared.generated.resources.force_update_title
import whl.trending.ai.core.Constants
import whl.trending.ai.core.platform.getAppVersion
import whl.trending.ai.core.platform.openUrl
import whl.trending.ai.core.platform.trackEvent
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.data.remote.TrendingApi
import whl.trending.ai.update.isVersionBlocked

/**
 * 强制更新门：当前版本低于服务端下发的 min_version 时，整屏替换主界面，
 * 引导用户去官方渠道更新（应对第三方镜像站收录的老包）。
 *
 * - 冷启动拉取 /api/app-config，成功即缓存；失败静默（接口未上线/断网不影响使用）。
 * - 初始态用缓存判定，保证强更一旦下发、离线重启也拦得住。
 * - 整屏替换而非弹窗：没有 dismiss 语义可绕过，也天然避免与 What's New 双弹窗叠加。
 */
@Composable
fun ForceUpdateGate(content: @Composable () -> Unit) {
    val current = remember { getAppVersion() }
    var minVersion by remember { mutableStateOf(globalSettingsManager.getCachedMinVersion()) }

    LaunchedEffect(Unit) {
        runCatching { TrendingApi().fetchAppConfig() }.onSuccess { config ->
            globalSettingsManager.setCachedMinVersion(config.minVersion)
            minVersion = config.minVersion
        }.onFailure {
            // fail-open：接口未上线（404）/断网都不影响使用，仅留日志便于排查服务端误配置
            println("ForceUpdateGate: fetchAppConfig failed: $it")
        }
    }

    if (isVersionBlocked(current, minVersion)) {
        LaunchedEffect(minVersion) {
            trackEvent(
                "force_update_shown",
                mapOf("current_version" to current, "min_version" to minVersion.orEmpty()),
            )
        }
        ForceUpdateScreen(currentVersion = current)
    } else {
        content()
    }
}

@Composable
private fun ForceUpdateScreen(currentVersion: String) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Default.SystemUpdate,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(Res.string.force_update_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(Res.string.force_update_message, currentVersion),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            Button(onClick = {
                trackEvent("force_update_click")
                openUrl(Constants.OFFICIAL_WEBSITE_URL)
            }) {
                Text(stringResource(Res.string.force_update_button))
            }
        }
    }
}
