package whl.trending.ai.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import trendingai.shared.generated.resources.login_email_hint
import trendingai.shared.generated.resources.login_email_invalid
import trendingai.shared.generated.resources.login_generic_error
import trendingai.shared.generated.resources.login_oauth_failed
import trendingai.shared.generated.resources.login_or
import trendingai.shared.generated.resources.login_resend
import trendingai.shared.generated.resources.login_resend_in
import trendingai.shared.generated.resources.login_title
import trendingai.shared.generated.resources.login_too_many_attempts
import trendingai.shared.generated.resources.login_too_many_requests
import wang.harlon.eventbase.Eventbase
import wang.harlon.loginbase.AuthError
import wang.harlon.loginbase.LoginbaseException
import whl.trending.ai.auth.GithubAuthResult
import whl.trending.ai.auth.LoginSheetBus
import whl.trending.ai.auth.LoginbaseAuthManager
import whl.trending.ai.auth.globalAuthManager
import whl.trending.ai.auth.launchGithubSignIn
import whl.trending.ai.core.analytics.AppEvent
import whl.trending.ai.core.analytics.AuthAction
import whl.trending.ai.core.analytics.AuthOutcome
import whl.trending.ai.core.analytics.Screen
import whl.trending.ai.core.analytics.track
import whl.trending.ai.core.analytics.trackOverlayScreenView
import whl.trending.ai.ui.common.TrendingBottomSheet
import whl.trending.ai.ui.home.githubLogoPainter

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

    // auth_started 与 flow 的开启都在 LoginSheetBus.request()——**不要挪回这里**：
    // 挂在 composition 上的副作用会随 Activity 重建重跑，把漏斗分母做成两倍

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

    // 登录漏斗的分母（telemetry-design L2 核心第 7 条）：打开登录界面就走 vs 输了邮箱卡在
    // 验证码，是两种流失、两种改法。全 app 唯一手写的 screen_viewed——浮层不进 backStack，
    // 路由源看不见它；用 overlay 变体上报，从而不打断 from 链（浮层不改变「在哪一页」）。
    LaunchedEffect(Unit) { trackOverlayScreenView(Screen.LOGIN) }

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

    // 埋点归因**不能**复用 describe 的返回值：那是本地化文案，进了 props 就是多语言高基数脏值
    fun reasonOf(e: Throwable): String = when (e) {
        is LoginbaseException.Api -> e.rawError
        is LoginbaseException.Network -> "network"
        is LoginbaseException.MalformedResponse -> "malformed_response"
        else -> "unknown"
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
                .onFailure {
                    error = describe(it)
                    // 发码这段没有终态事件的话，「输了邮箱→没收到码」的流失在漏斗里是暗的
                    track(
                        AppEvent.AuthFinished(
                            AuthAction.SIGN_IN,
                            AuthOutcome.ERROR,
                            method = "email",
                            source = source,
                            reason = reasonOf(it),
                        ),
                        Eventbase.currentFlow(),
                    )
                }
            busy = false
        }
    }

    // GitHub 授权回跳：面板**不直接订阅** client.oauthResults——授权要跳出 App，回来时
    // 面板可能已经不在了（进程被回收 → 冷启动），那条结果就没有消费者，会滞留在
    // replay 里、在下一次面板打开的瞬间炸出来。统一由常驻的 OAuthOutcomeHost 消费，
    // 埋点也归它（那样冷启动路径上的成功/失败才报得出去）；面板只读需要显示的部分。
    // 成功不在这里处理：宿主会直接清掉 LoginSheetBus，面板随之消失。
    val githubResult by LoginSheetBus.githubResult.collectAsState()
    LaunchedEffect(githubResult) {
        when (githubResult) {
            GithubAuthResult.FAILED -> {
                error = oauthFailed
                busy = false
            }
            // 用户放弃授权（关掉 CCT / 从浏览器返回）——库给的确定信号，不报错只解除等待
            GithubAuthResult.CANCELED -> busy = false
            null -> Unit
        }
    }

    fun verify() {
        error = null
        busy = true
        scope.launch {
            runCatching { client.verifyCode(email.trim(), code) }
                .onSuccess {
                    track(
                        AppEvent.AuthFinished(
                            AuthAction.SIGN_IN,
                            AuthOutcome.SUCCESS,
                            method = "email",
                            source = source,
                            isNew = it.isNewUser == true,
                        ),
                        Eventbase.currentFlow(),
                    )
                    onDismiss()
                }
                .onFailure {
                    error = describe(it)
                    track(
                        AppEvent.AuthFinished(
                            AuthAction.SIGN_IN,
                            AuthOutcome.ERROR,
                            method = "email",
                            source = source,
                            reason = reasonOf(it),
                        ),
                        Eventbase.currentFlow(),
                    )
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
                        track(
                            AppEvent.AuthStarted(AuthAction.SIGN_IN, method = "github", source = source),
                            Eventbase.currentFlow(),
                        )
                        // 浏览器环节归 loginbase-kt-browser（Auth Tab/CCT/系统浏览器
                        // 按可用性回退），结果从上方的 oauthResults 收
                        if (!launchGithubSignIn(client)) {
                            busy = false
                            error = genericError
                            // 浏览器没起来就不会有回跳，终态只能在这里补，否则 started 落单
                            track(
                                AppEvent.AuthFinished(
                                    AuthAction.SIGN_IN,
                                    AuthOutcome.ERROR,
                                    method = "github",
                                    source = source,
                                    reason = "launch_failed",
                                ),
                                Eventbase.currentFlow(),
                            )
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
