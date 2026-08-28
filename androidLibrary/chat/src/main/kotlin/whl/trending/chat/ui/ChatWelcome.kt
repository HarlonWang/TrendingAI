package whl.trending.chat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import whl.trending.ai.auth.AuthState
import whl.trending.ai.auth.globalAuthManager
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.ui.profile.ProBadge
import whl.trending.chat.R

/** 欢迎区的额度口径档位，与服务端 `resolveQuotaTier` 的三档同名同义。 */
internal enum class WelcomeTier { Anonymous, Free, Pro }

/**
 * 空状态欢迎区，尚无任何对话时显示。上下文解读入口（[hasContext] = true）的标题与快捷问
 * 已在别处呈现，只保留额度说明一行。
 */
@Composable
fun ChatWelcome(hasContext: Boolean, modifier: Modifier = Modifier) {
    // 档位判据取本地缓存而非 GET /api/quota：一行小字不值得打网络。失准窗口只有「订阅已到期
    // 且 app 未冷启」，下次冷启的 syncMe 即纠正（唯一日常写入点见 App.kt 根部 LaunchedEffect）
    val isPro by globalSettingsManager.isPro.collectAsState(
        // initial 必须是同步值：给 false 会让 Pro 用户先闪一帧免费档文案
        initial = globalSettingsManager.currentIsPro(),
    )
    val authState by globalAuthManager.authState.collectAsState()
    val tier = when {
        isPro -> WelcomeTier.Pro
        authState == AuthState.LoggedIn -> WelcomeTier.Free
        else -> WelcomeTier.Anonymous
    }
    ChatWelcomeContent(
        hasContext = hasContext,
        tier = tier,
        canSignIn = globalAuthManager.isSupported,
        onSignIn = { globalAuthManager.signIn("chat_welcome") },
        modifier = modifier,
    )
}

@Composable
internal fun ChatWelcomeContent(
    hasContext: Boolean,
    tier: WelcomeTier,
    canSignIn: Boolean,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (!hasContext) {
            Text(text = "✨", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.chat_assistant_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(16.dp))
        }
        if (tier == WelcomeTier.Pro) {
            ProBadge()
            Spacer(Modifier.height(8.dp))
        }
        Text(
            text = stringResource(
                when (tier) {
                    WelcomeTier.Pro -> R.string.chat_quota_notice_pro
                    WelcomeTier.Free -> R.string.chat_quota_notice_user
                    WelcomeTier.Anonymous -> R.string.chat_quota_notice
                }
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        // 匿名档的登录 CTA 与触顶卡同源：身份引导，不是付费引导（取舍见 QuotaLimitCard 头注释）
        if (tier == WelcomeTier.Anonymous && canSignIn) {
            TextButton(onClick = onSignIn) {
                Text(stringResource(R.string.chat_quota_login_cta))
            }
        } else {
            Spacer(Modifier.height(6.dp))
        }
        // 模型出处不做视觉强调：OpenAI 品牌指南要求其展示不得比我们自己的名称更显著
        Text(
            text = stringResource(R.string.chat_model_provider),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatWelcomeAnonymousPreview() {
    MaterialTheme {
        ChatWelcomeContent(hasContext = false, tier = WelcomeTier.Anonymous, canSignIn = true, onSignIn = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatWelcomeFreePreview() {
    MaterialTheme {
        ChatWelcomeContent(hasContext = false, tier = WelcomeTier.Free, canSignIn = true, onSignIn = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatWelcomeProPreview() {
    MaterialTheme {
        ChatWelcomeContent(hasContext = false, tier = WelcomeTier.Pro, canSignIn = true, onSignIn = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatWelcomeContextPreview() {
    MaterialTheme {
        ChatWelcomeContent(hasContext = true, tier = WelcomeTier.Free, canSignIn = true, onSignIn = {})
    }
}
