package whl.trending.chat.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import whl.trending.ai.auth.AuthState
import whl.trending.ai.auth.globalAuthManager
import whl.trending.ai.core.ProSponsor
import whl.trending.ai.core.isValidEmail
import whl.trending.ai.core.platform.trackEvent
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.data.repository.TrendingRepository
import whl.trending.chat.R
import whl.trending.chat.model.ChatError

/**
 * 个人配额触顶卡片（`quota_device`），按档位分形态：
 * - 匿名触顶：登录 CTA（转化点）+ waitlist 次按钮
 * - 匿名触顶后完成登录：提示已解锁，给重试按钮直接续聊
 * - 登录触顶：waitlist CTA（付费意愿温度计）
 *
 * 全局熔断（`quota_global`）不走本卡片，仍是普通错误文案——语义上与个人额度承诺切开。
 */
@Composable
internal fun QuotaLimitCard(
    error: ChatError,
    onRetry: () -> Unit,
) {
    val authState by globalAuthManager.authState.collectAsState()
    val isProTier = error.tier == ChatError.TIER_PRO
    val isUserTier = error.tier == ChatError.TIER_USER
    var showWaitlistDialog by remember { mutableStateOf(false) }

    if (showWaitlistDialog) {
        WaitlistDialog(onDismiss = { showWaitlistDialog = false })
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when {
            isProTier -> {
                // Pro 触顶（极罕见）：不透数字的软着陆，无 CTA（已是 Pro，明日恢复）
                QuotaText(R.string.chat_quota_pro_exceeded)
            }
            isUserTier -> {
                // 付费漏斗第一级（曝光）：登录触顶卡带 Pro CTA 渲染。去重靠 LaunchedEffect(Unit)。
                LaunchedEffect(Unit) {
                    ProSponsor.trackUpsellShown(ProSponsor.SOURCE_CHAT_QUOTA)
                }
                QuotaText(R.string.chat_pro_upsell_message)
                Button(onClick = {
                    ProSponsor.openSponsorPage(ProSponsor.SOURCE_CHAT_QUOTA)
                }) {
                    Text(stringResource(R.string.chat_pro_cta))
                }
                // 次按钮：付不了国际卡的人 → waitlist（捕获支付摩擦样本）
                TextButton(onClick = {
                    trackEvent("chat_quota_waitlist_click", mapOf("tier" to ChatError.TIER_USER))
                    showWaitlistDialog = true
                }) {
                    Text(stringResource(R.string.chat_quota_waitlist_cta))
                }
                // 激活延迟提示（人工兜底可能有延迟）
                QuotaText(R.string.chat_pro_activation_hint)
            }
            authState == AuthState.LoggedIn -> {
                if (error.authDegraded) {
                    // 发请求时就自认已登录、却被按匿名档处理（token 刷新失败/被拒）：
                    // 如实提示登录态未生效，不给「已解锁、重发即可」的死循环误导
                    QuotaText(R.string.chat_quota_auth_degraded)
                } else {
                    // 匿名触顶后完成了登录：配额键已切换，重发即可继续
                    QuotaText(R.string.chat_quota_unlocked)
                }
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.chat_retry))
                }
            }
            else -> {
                QuotaText(R.string.chat_quota_exceeded)
                if (globalAuthManager.isSupported) {
                    Button(onClick = {
                        trackEvent("chat_quota_login_click")
                        globalAuthManager.signIn()
                    }) {
                        Text(stringResource(R.string.chat_quota_login_cta))
                    }
                }
                TextButton(onClick = {
                    trackEvent("chat_quota_waitlist_click", mapOf("tier" to ChatError.TIER_ANONYMOUS))
                    showWaitlistDialog = true
                }) {
                    Text(stringResource(R.string.chat_quota_waitlist_cta))
                }
            }
        }
    }
}

@Composable
private fun QuotaText(resId: Int) {
    Text(
        text = stringResource(resId),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Pro waitlist 登记：邮箱写入 subscribers（source=pro_waitlist），复用邮件订阅通道。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WaitlistDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var email by remember {
        mutableStateOf(globalSettingsManager.currentSubscribedEmail().orEmpty())
    }
    // 每日邮件严格 opt-in：默认不勾选，登记 waitlist 不自动开通 newsletter
    var wantsNewsletter by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    val successText = stringResource(R.string.chat_waitlist_success)
    val errorText = stringResource(R.string.chat_waitlist_error)

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text(stringResource(R.string.chat_waitlist_title)) },
        text = {
            Column {
                Text(stringResource(R.string.chat_waitlist_message))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.chat_waitlist_email_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .clickable(enabled = !isSubmitting) { wantsNewsletter = !wantsNewsletter },
                ) {
                    Checkbox(
                        checked = wantsNewsletter,
                        onCheckedChange = { wantsNewsletter = it },
                        enabled = !isSubmitting,
                    )
                    Text(
                        text = stringResource(R.string.chat_waitlist_newsletter_optin),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSubmitting && isValidEmail(email),
                onClick = {
                    isSubmitting = true
                    scope.launch {
                        val result = TrendingRepository.shared.subscribe(
                            email.trim(),
                            "pro_waitlist",
                            // 与 SubscribeViewModel 同口径：同一 subscribers 表不留两种 lang 推导
                            globalSettingsManager.currentContentLang(),
                            newsletter = wantsNewsletter,
                        )
                        isSubmitting = false
                        if (result.isSuccess) {
                            trackEvent("chat_quota_waitlist_submitted")
                            // 顺带开通了 newsletter：同步本地订阅态，订阅页才能识别已订阅、给出退订入口
                            if (wantsNewsletter) {
                                globalSettingsManager.setSubscribedEmail(email.trim())
                            }
                            Toast.makeText(context, successText, Toast.LENGTH_SHORT).show()
                            onDismiss()
                        } else {
                            Toast.makeText(context, errorText, Toast.LENGTH_SHORT).show()
                        }
                    }
                },
            ) {
                if (isSubmitting) {
                    LoadingIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Text(stringResource(R.string.chat_waitlist_submit))
                }
            }
        },
        dismissButton = {
            TextButton(enabled = !isSubmitting, onClick = onDismiss) {
                Text(stringResource(R.string.chat_waitlist_cancel))
            }
        },
    )
}

