package whl.trending.ai.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.sign_in_method_email
import trendingai.shared.generated.resources.sign_in_method_email_desc
import trendingai.shared.generated.resources.sign_in_method_github
import trendingai.shared.generated.resources.sign_in_method_github_desc
import trendingai.shared.generated.resources.sign_in_method_title
import whl.trending.ai.auth.SignInChooserBus
import whl.trending.ai.auth.SignInMethod
import whl.trending.ai.auth.globalAuthManager
import whl.trending.ai.core.platform.trackEvent
import whl.trending.ai.ui.home.githubLogoPainter

/**
 * 登录方式选择器宿主：收集 [SignInChooserBus] 的待选请求弹底部选择（GitHub / 邮箱验证码），
 * 选中后回调 `signIn(source, method)` 拉起对应授权流程。
 *
 * 放在 App 根部（与 SignInHintHost 平级）：登录入口有 6 处（首页头像 / star Snackbar ×2 /
 * chat 配额卡 ×2 / 图片理解弹窗），总线 + 根部宿主让所有入口零改动共享同一选择器。
 *
 * 埋点：shown/dismiss 记录选择器层的流失；方式一经选中，后续 sign_in_start/success/canceled/error
 * 全部带 method 属性（见 LogtoAuthManager）——失败漏斗从此可按登录方式分段。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInMethodChooserHost() {
    val source by SignInChooserBus.request.collectAsState()
    val pendingSource = source ?: return

    LaunchedEffect(pendingSource) {
        trackEvent("sign_in_method_shown", mapOf("source" to pendingSource))
    }

    val choose = { method: SignInMethod ->
        SignInChooserBus.clear()
        globalAuthManager.signIn(pendingSource, method)
    }
    ModalBottomSheet(
        onDismissRequest = {
            trackEvent("sign_in_method_dismiss", mapOf("source" to pendingSource))
            SignInChooserBus.clear()
        },
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                text = stringResource(Res.string.sign_in_method_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            ListItem(
                headlineContent = { Text(stringResource(Res.string.sign_in_method_github)) },
                supportingContent = { Text(stringResource(Res.string.sign_in_method_github_desc)) },
                leadingContent = {
                    Icon(
                        painter = githubLogoPainter(),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(24.dp),
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.fillMaxWidth().clickable { choose(SignInMethod.GITHUB) },
            )
            ListItem(
                headlineContent = { Text(stringResource(Res.string.sign_in_method_email)) },
                supportingContent = { Text(stringResource(Res.string.sign_in_method_email_desc)) },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Outlined.Email,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.fillMaxWidth().clickable { choose(SignInMethod.EMAIL) },
            )
        }
    }
}
