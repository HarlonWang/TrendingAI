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
import trendingai.shared.generated.resources.sign_in_hint_clock_skew
import trendingai.shared.generated.resources.sign_in_hint_connectivity
import trendingai.shared.generated.resources.sign_in_hint_dismiss
import trendingai.shared.generated.resources.sign_in_hint_relogin
import trendingai.shared.generated.resources.sign_in_hint_session_expired
import trendingai.shared.generated.resources.sign_in_hint_session_expired_title
import trendingai.shared.generated.resources.sign_in_hint_title
import whl.trending.ai.auth.SignInFailureBus
import whl.trending.ai.auth.SignInFailureReason
import whl.trending.ai.auth.globalAuthManager

/**
 * 全局登录失败提示宿主：收集 [SignInFailureBus] 的失败事件，对可行动的失败类别弹轻量弹窗——
 * 连通性类（NETWORK/TIMEOUT）引导检查网络；时钟偏差类（CLOCK_SKEW）引导修系统时间，
 * 后者若只给通用提示，用户会陷入「重试永远失败」的死循环（时钟不修，id_token 校验必败）。
 * 会话失效类（SESSION_EXPIRED）引导重新登录：本地会话已被清除，确认键直接拉起登录选择器。
 *
 * 用弹窗（独立窗口）而非 Snackbar：① 不占/不盖 app 布局；② 登录失败值得被明确看见，Snackbar 易被错过。
 * 放在 App 根部（与 WhatsNewHost 平级），是因为登录有 4 个触发点（首页头像 / Trending·Readme 的
 * star Snackbar / chat 配额卡），OAuth 走系统浏览器后可能回到任意页面；根部宿主对所有入口统一生效。
 */
@Composable
fun SignInHintHost() {
    var hint by remember { mutableStateOf<SignInFailureReason?>(null) }

    LaunchedEffect(Unit) {
        SignInFailureBus.events.collect { reason ->
            // 收到即消费：清掉 replay 缓存，避免之后重建的收集者（旋转/主题切换）把旧失败再弹一遍
            SignInFailureBus.consume()
            if (reason.shouldHint()) hint = reason
        }
    }

    hint?.let { reason ->
        val sessionExpired = reason == SignInFailureReason.SESSION_EXPIRED
        AlertDialog(
            onDismissRequest = { hint = null },
            title = {
                Text(
                    stringResource(
                        if (sessionExpired) Res.string.sign_in_hint_session_expired_title
                        else Res.string.sign_in_hint_title,
                    ),
                )
            },
            text = {
                Text(
                    stringResource(
                        when (reason) {
                            SignInFailureReason.SESSION_EXPIRED -> Res.string.sign_in_hint_session_expired
                            SignInFailureReason.CLOCK_SKEW -> Res.string.sign_in_hint_clock_skew
                            else -> Res.string.sign_in_hint_connectivity
                        },
                    ),
                )
            },
            confirmButton = {
                if (sessionExpired) {
                    // 浏览器侧 Logto 会话通常还在（本地只清了凭证），重登多为一跳静默回到原账号
                    TextButton(onClick = {
                        hint = null
                        globalAuthManager.signIn("session_expired_hint")
                    }) {
                        Text(stringResource(Res.string.sign_in_hint_relogin))
                    }
                } else {
                    TextButton(onClick = { hint = null }) {
                        Text(stringResource(Res.string.sign_in_hint_dismiss))
                    }
                }
            },
            dismissButton = if (sessionExpired) {
                {
                    TextButton(onClick = { hint = null }) {
                        Text(stringResource(Res.string.sign_in_hint_dismiss))
                    }
                }
            } else null,
        )
    }
}

/**
 * 是否值得弹提示：仅限用户可行动的失败（查网络 / 修时钟 / 重新登录）。
 *
 * USER_CANCELED 不弹——用户主动关闭授权页是常见操作，弹提示既误导又打扰。代价是已缓存 OIDC 配置的
 * 老用户断网登录（错误落在浏览器内、被归为 USER_CANCELED）收不到提示，这是取舍后的选择。
 * CONFIG / NO_BROWSER / OTHER 用户无从行动，同样不弹。
 */
private fun SignInFailureReason.shouldHint(): Boolean = when (this) {
    SignInFailureReason.NETWORK,
    SignInFailureReason.TIMEOUT,
    SignInFailureReason.CLOCK_SKEW,
    SignInFailureReason.SESSION_EXPIRED -> true
    SignInFailureReason.USER_CANCELED,
    SignInFailureReason.NO_BROWSER,
    SignInFailureReason.CONFIG,
    SignInFailureReason.OTHER -> false
}
