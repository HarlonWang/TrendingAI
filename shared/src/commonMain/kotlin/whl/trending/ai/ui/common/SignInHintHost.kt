package whl.trending.ai.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.sign_in_hint_connectivity
import trendingai.shared.generated.resources.sign_in_hint_dismiss
import trendingai.shared.generated.resources.sign_in_hint_title
import whl.trending.ai.auth.SignInFailureReason
import whl.trending.ai.auth.globalAuthManager

/**
 * 全局登录失败提示宿主：收集 [globalAuthManager] 的失败事件，命中「连通性类」失败时弹一个轻量弹窗
 * 引导用户检查网络。
 *
 * 用弹窗（独立窗口）而非 Snackbar：① 不占/不盖 app 布局；② 登录失败值得被明确看见，Snackbar 易被错过。
 * 放在 App 根部（与 WhatsNewHost 平级），是因为登录有 4 个触发点（首页头像 / Trending·Readme 的
 * star Snackbar / chat 配额卡），OAuth 走系统浏览器后可能回到任意页面；根部宿主对所有入口统一生效。
 */
@Composable
fun SignInHintHost() {
    var show by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        globalAuthManager.signInFailures.collect { reason ->
            if (reason.shouldHintConnectivity()) show = true
        }
    }

    if (show) {
        AlertDialog(
            onDismissRequest = { show = false },
            title = { Text(stringResource(Res.string.sign_in_hint_title)) },
            text = { Text(stringResource(Res.string.sign_in_hint_connectivity)) },
            confirmButton = {
                TextButton(onClick = { show = false }) {
                    Text(stringResource(Res.string.sign_in_hint_dismiss))
                }
            },
        )
    }
}

/**
 * 是否属于「网络可能不通」的失败，值得提示用户检查连通性。
 *
 * 只在明确的网络类失败（NETWORK / TIMEOUT）时弹：USER_CANCELED 不弹——用户主动关闭授权页是常见操作，
 * 弹「检查网络」既误导又打扰。代价是已缓存 OIDC 配置的老用户断网登录（错误落在浏览器内、被归为
 * USER_CANCELED）收不到提示，这是取舍后的选择。CONFIG / NO_BROWSER / OTHER 与连通性无关，同样不弹。
 */
private fun SignInFailureReason.shouldHintConnectivity(): Boolean = when (this) {
    SignInFailureReason.NETWORK,
    SignInFailureReason.TIMEOUT -> true
    SignInFailureReason.USER_CANCELED,
    SignInFailureReason.NO_BROWSER,
    SignInFailureReason.CONFIG,
    SignInFailureReason.OTHER -> false
}
