package whl.trending.ai.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.login_code_expired
import trendingai.shared.generated.resources.login_code_hint
import trendingai.shared.generated.resources.login_code_invalid
import trendingai.shared.generated.resources.login_code_sent_to
import trendingai.shared.generated.resources.login_code_title
import trendingai.shared.generated.resources.login_continue
import trendingai.shared.generated.resources.login_continue_github
import trendingai.shared.generated.resources.login_oauth_failed
import trendingai.shared.generated.resources.login_or
import trendingai.shared.generated.resources.login_email_hint
import trendingai.shared.generated.resources.login_email_invalid
import trendingai.shared.generated.resources.login_generic_error
import trendingai.shared.generated.resources.login_resend
import trendingai.shared.generated.resources.login_resend_in
import trendingai.shared.generated.resources.login_title
import trendingai.shared.generated.resources.login_too_many_attempts
import trendingai.shared.generated.resources.login_too_many_requests
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import wang.harlon.loginbase.LoginbaseException
import wang.harlon.loginbase.AuthError
import whl.trending.ai.auth.LoginSheetBus
import whl.trending.ai.auth.LoginbaseAuthManager
import whl.trending.ai.auth.globalAuthManager
import wang.harlon.loginbase.OAuthOutcome
import whl.trending.ai.auth.launchGithubSignIn
import whl.trending.ai.core.platform.trackEvent
import whl.trending.ai.ui.home.githubLogoPainter
import whl.trending.ai.ui.common.TrendingBottomSheet

/**
 * App 内登录面板宿主。挂在 App 根部（与 SignInHintHost 平级），收 [LoginSheetBus]
 * 的请求弹面板——6 个登录入口零改动。
 *
 * 与 Logto 时代的方式选择器不同：那时两条登录路都要跳出 App，App 内没有 UI 可以
 * 承载选择；现在邮箱验证码全程原生，面板本身就是登录页，GitHub 按钮同屏并列。
 */
@Composable
fun LoginSheetHost() {
    val source by LoginSheetBus.request.collectAsState()
    val pendingSource = source ?: return

    LaunchedEffect(pendingSource) {
        trackEvent("sign_in_start", mapOf("source" to pendingSource, "method" to "sheet"))
    }

    LoginSheet(
        source = pendingSource,
        onDismiss = { LoginSheetBus.clear() },
    )
}

private enum class Step { EMAIL, CODE }

// TrendingBottomSheet 的默认 sheetState 与 LoadingIndicator 都是实验 API
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoginSheet(source: String, onDismiss: () -> Unit) {
    val manager = globalAuthManager as? LoginbaseAuthManager
    if (manager == null) {
        // 尚未初始化（理论上不会发生，MainActivity 里已注入）——静默收起，别卡住用户
        onDismiss()
        return
    }
    val client = manager.client
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(Step.EMAIL) }
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var cooldown by remember { mutableStateOf(0) }

    val genericError = stringResource(Res.string.login_generic_error)
    val emailInvalid = stringResource(Res.string.login_email_invalid)
    val codeInvalid = stringResource(Res.string.login_code_invalid)
    val codeExpired = stringResource(Res.string.login_code_expired)
    val tooManyAttempts = stringResource(Res.string.login_too_many_attempts)
    val tooManyRequestsTemplate = stringResource(Res.string.login_too_many_requests)
    val oauthFailed = stringResource(Res.string.login_oauth_failed)

    // 服务端给的冷却秒数倒计时，不写死 60
    LaunchedEffect(cooldown) {
        if (cooldown > 0) {
            delay(1000)
            cooldown -= 1
        }
    }

    fun describe(e: Throwable): String = when {
        e !is LoginbaseException.Api -> genericError
        e.error == AuthError.INVALID_EMAIL -> emailInvalid
        e.error == AuthError.INVALID_CODE -> codeInvalid
        e.error == AuthError.CODE_EXPIRED -> codeExpired
        e.error == AuthError.TOO_MANY_ATTEMPTS -> tooManyAttempts
        e.error == AuthError.TOO_MANY_REQUESTS ->
            tooManyRequestsTemplate.replace("%d", (e.retryAfterSeconds ?: 60).toString())
        else -> genericError
    }

    fun send() {
        error = null
        busy = true
        scope.launch {
            runCatching { client.sendCode(email.trim()) }
                .onSuccess {
                    cooldown = it.cooldownSeconds
                    step = Step.CODE
                    code = ""
                }
                .onFailure { error = describe(it) }
            busy = false
        }
    }

    // GitHub 授权回跳：结果从库的唯一通道送达。otc 兑换、登录/绑定分辨、取消判定
    // 都已在库内完成——过去靠 ON_RESUME 启发式兜「关掉浏览器没有任何回调」的那段
    // 没有了，用户关掉授权页会收到确定的 Cancelled。
    // 面板此时通常还开着（Activity 未被销毁），故由面板自己收尾最自然。
    LaunchedEffect(Unit) {
        client.oauthResults.collect { outcome ->
            when (outcome) {
                is OAuthOutcome.SignedIn -> {
                    client.consumeOauthResult()
                    trackEvent(
                        "sign_in_success",
                        mapOf(
                            "source" to source,
                            "method" to "github",
                            "is_new" to (outcome.session.isNewUser == true),
                        ),
                    )
                    busy = false
                    onDismiss()
                }
                is OAuthOutcome.Failed -> {
                    client.consumeOauthResult()
                    error = oauthFailed
                    busy = false
                    trackEvent("sign_in_error", mapOf("source" to source, "method" to "github"))
                }
                OAuthOutcome.Cancelled -> {
                    // 用户放弃授权（关掉 CCT / 从浏览器返回）——库的确定信号
                    client.consumeOauthResult()
                    busy = false
                }
                // 绑定身份的回跳不归登录面板管，留给账户页消费
                is OAuthOutcome.Linked -> Unit
                // 配置类异常输入（详见库文档），开发期问题，不打扰用户
                is OAuthOutcome.Unrecognized -> Unit
            }
        }
    }

    fun verify() {
        error = null
        busy = true
        scope.launch {
            runCatching { client.verifyCode(email.trim(), code) }
                .onSuccess {
                    trackEvent(
                        "sign_in_success",
                        mapOf("source" to source, "method" to "email", "is_new" to (it.isNewUser == true)),
                    )
                    onDismiss()
                }
                .onFailure {
                    error = describe(it)
                    trackEvent("sign_in_error", mapOf("source" to source, "method" to "email"))
                }
            busy = false
        }
    }

    TrendingBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(if (step == Step.EMAIL) Res.string.login_title else Res.string.login_code_title),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(PaddingValues(horizontal = 24.dp, vertical = 8.dp)),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (step) {
                Step.EMAIL -> {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text(stringResource(Res.string.login_email_hint)) },
                        singleLine = true,
                        enabled = !busy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Step.CODE -> {
                    Text(
                        text = stringResource(Res.string.login_code_sent_to).replace("%s", email.trim()),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = code,
                        onValueChange = { input ->
                            code = input.filter { it.isDigit() }.take(6)
                            // 填满 6 位自动提交，省一次点击
                            if (code.length == 6 && !busy) verify()
                        },
                        label = { Text(stringResource(Res.string.login_code_hint)) },
                        singleLine = true,
                        enabled = !busy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(
                        onClick = { send() },
                        enabled = !busy && cooldown == 0,
                    ) {
                        Text(
                            if (cooldown > 0) {
                                stringResource(Res.string.login_resend_in).replace("%d", cooldown.toString())
                            } else {
                                stringResource(Res.string.login_resend)
                            }
                        )
                    }
                }
            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            if (step == Step.EMAIL) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HorizontalDivider(modifier = Modifier.weight(1f))
                    Text(
                        text = stringResource(Res.string.login_or),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f))
                }
                OutlinedButton(
                    onClick = {
                        error = null
                        busy = true
                        trackEvent("sign_in_start", mapOf("source" to source, "method" to "github"))
                        // 浏览器环节归 loginbase-kt-browser（Auth Tab/CCT/系统浏览器
                        // 按可用性回退），结果从上方的 oauthResults 收
                        if (!launchGithubSignIn(client)) {
                            busy = false
                            error = genericError
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Image(painter = githubLogoPainter(), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        text = stringResource(Res.string.login_continue_github),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            Button(
                onClick = { if (step == Step.EMAIL) send() else verify() },
                enabled = !busy && when (step) {
                    Step.EMAIL -> email.contains('@')
                    Step.CODE -> code.length == 6
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (busy) {
                    LoadingIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(Res.string.login_continue))
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
