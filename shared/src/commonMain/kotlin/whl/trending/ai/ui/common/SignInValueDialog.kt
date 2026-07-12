package whl.trending.ai.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.sign_in
import trendingai.shared.generated.resources.sign_in_value_chat
import trendingai.shared.generated.resources.sign_in_value_dismiss
import trendingai.shared.generated.resources.sign_in_value_footer
import trendingai.shared.generated.resources.sign_in_value_profile
import trendingai.shared.generated.resources.sign_in_value_star
import trendingai.shared.generated.resources.sign_in_value_title
import whl.trending.ai.core.platform.trackEvent

/**
 * 登录价值说明弹窗：在拉起 Logto 网页授权前，先告诉用户登录能得到什么。
 *
 * 背景：埋点显示登录失败几乎全是 user_canceled——用户被直接抛进陌生的浏览器授权页，
 * 不知道登录有什么用就放弃了。此弹窗前置价值点并预告「会跳转浏览器」，降低授权中途放弃率。
 * 只挂在无场景上下文的入口（首页头像）；star Snackbar / chat 配额卡等入口自带场景化说明，不套此弹窗。
 *
 * 注意：价值点只列已上线能力（star / chat 配额 / GitHub 主页），未上线的收藏同步不得出现在这里。
 *
 * @param source 埋点来源标识（如 "home_avatar"），随 sign_in_value_* 事件上报
 */
@Composable
fun SignInValueDialog(
    source: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(Unit) { trackEvent("sign_in_value_shown", mapOf("source" to source)) }

    // method 区分「暂不」按钮（button）与外点/返回键（outside）：明确拒绝和随手关闭对文案迭代的含义不同
    val dismiss = { method: String ->
        trackEvent("sign_in_value_dismiss", mapOf("source" to source, "method" to method))
        onDismiss()
    }
    AlertDialog(
        onDismissRequest = { dismiss("outside") },
        title = { Text(stringResource(Res.string.sign_in_value_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                BenefitRow(Icons.Filled.Star, stringResource(Res.string.sign_in_value_star))
                BenefitRow(Icons.AutoMirrored.Filled.Chat, stringResource(Res.string.sign_in_value_chat))
                BenefitRow(Icons.Filled.Person, stringResource(Res.string.sign_in_value_profile))
                Text(
                    text = stringResource(Res.string.sign_in_value_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                trackEvent("sign_in_value_confirm", mapOf("source" to source))
                onConfirm()
            }) {
                Text(stringResource(Res.string.sign_in))
            }
        },
        dismissButton = {
            TextButton(onClick = { dismiss("button") }) {
                Text(stringResource(Res.string.sign_in_value_dismiss))
            }
        },
    )
}

@Composable
private fun BenefitRow(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}
