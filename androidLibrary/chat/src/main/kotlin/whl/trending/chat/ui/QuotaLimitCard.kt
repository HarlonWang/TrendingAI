package whl.trending.chat.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import whl.trending.ai.auth.AuthState
import whl.trending.ai.auth.globalAuthManager
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
    val isUserTier = error.tier == "user"
    var showWaitlistDialog by remember { mutableStateOf(false) }

    if (showWaitlistDialog) {
        WaitlistDialog(onDismiss = { showWaitlistDialog = false })
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when {
            isUserTier -> {
                QuotaText(R.string.chat_quota_user_exceeded)
                TextButton(onClick = {
                    trackEvent("chat_quota_waitlist_click", mapOf("tier" to "user"))
                    showWaitlistDialog = true
                }) {
                    Text(stringResource(R.string.chat_quota_waitlist_cta))
                }
            }
            authState == AuthState.LoggedIn -> {
                // 匿名触顶后完成了登录：配额键已切换，重发即可继续
                QuotaText(R.string.chat_quota_unlocked)
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
                    trackEvent("chat_quota_waitlist_click", mapOf("tier" to "anonymous"))
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
@Composable
private fun WaitlistDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { TrendingRepository() }
    var email by remember {
        mutableStateOf(globalSettingsManager.getSubscribedEmailSync().orEmpty())
    }
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
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSubmitting && email.contains('@'),
                onClick = {
                    isSubmitting = true
                    scope.launch {
                        val result = repository.subscribe(email.trim(), "pro_waitlist", resolveLang())
                        isSubmitting = false
                        if (result.isSuccess) {
                            trackEvent("chat_quota_waitlist_submitted")
                            Toast.makeText(context, successText, Toast.LENGTH_SHORT).show()
                            onDismiss()
                        } else {
                            Toast.makeText(context, errorText, Toast.LENGTH_SHORT).show()
                        }
                    }
                },
            ) {
                Text(stringResource(R.string.chat_waitlist_submit))
            }
        },
        dismissButton = {
            TextButton(enabled = !isSubmitting, onClick = onDismiss) {
                Text(stringResource(R.string.chat_waitlist_cancel))
            }
        },
    )
}

private suspend fun resolveLang(): String {
    val appLang = globalSettingsManager.appLanguage.first()
    return appLang.isoCode
        ?: if (Locale.getDefault().language == "zh") "zh" else "en"
}
