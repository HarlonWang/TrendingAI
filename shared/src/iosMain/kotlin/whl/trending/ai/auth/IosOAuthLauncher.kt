// publishOAuthOutcome 是库留给「配套模块」的投递口（loginbase-kt-browser 是 Android 那个），
// 本文件担的正是它在 iOS 的角色。不受语义化版本保护：升级 loginbase-kt 时先看这里。
@file:OptIn(LoginbaseInternalApi::class)

package whl.trending.ai.auth

import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.AuthenticationServices.ASWebAuthenticationSessionCallback
import platform.AuthenticationServices.ASWebAuthenticationSessionErrorCodeCanceledLogin
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.darwin.NSObject
import wang.harlon.loginbase.AuthClient
import wang.harlon.loginbase.LoginbaseException
import wang.harlon.loginbase.LoginbaseInternalApi
import wang.harlon.loginbase.OAuthOutcome
import wang.harlon.loginbase.OAuthProvider

/**
 * 回跳地址。**服务端 `AUTH_DEEPLINKS` 白名单必须含此值**，与 Android 的 release 变体同一个
 * （scheme 归属按平台隔离，同值不冲突）；iOS 没有 debug 变体，故不像 Android 那样从构建配置读。
 */
private const val REDIRECT_SCHEME = "cn.trendingai"
private const val REDIRECT_URI = "$REDIRECT_SCHEME:/loginbase/callback"

private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

/** 授权期间必须持有会话，否则它会被回收、回调永不到达 */
private var activeSession: ASWebAuthenticationSession? = null

private val anchorProvider = AnchorProvider()

/**
 * iOS 的 GitHub 授权接线，[whl.trending.ai.core.MainViewController] 调一次。
 *
 * 用 `ASWebAuthenticationSession` 而不是 Android 那套「浏览器 + 回跳捕获」：授权页由系统
 * 承载，回跳直接进 completionHandler，不经 App 冷启动——库里为 Android 备的停泊槽
 * （进程被回收后兑换回跳）在这条路上用不到。
 *
 * 结果一律投进 [AuthClient.oauthResults]，与 Android 同一个消费者
 * （[whl.trending.ai.ui.common.OAuthOutcomeHost]），埋点与错误文案因此全部复用。
 */
fun installIosOAuthLauncher() {
    globalOAuthLauncher = { client, mode, clientFlowId ->
        when (mode) {
            OAuthMode.SIGN_IN -> {
                // browser_tier 不上报：服务端白名单只收 Android 的三种通路，传别的会被静默丢弃
                val url = client.signInUrl(OAuthProvider.GitHub, REDIRECT_URI)
                    .appendClientFlowId(clientFlowId)
                startSession(client, url)
            }
            // link 的授权 URL 要带 Bearer 换取，多一次往返
            OAuthMode.LINK -> scope.launch {
                val url = try {
                    client.linkUrl(OAuthProvider.GitHub, REDIRECT_URI)
                } catch (e: LoginbaseException) {
                    client.publishOAuthOutcome(OAuthOutcome.Failed(e.failureReason()))
                    return@launch
                }
                startSession(client, url)
            }
        }
        true
    }
}

private fun startSession(client: AuthClient, url: String) {
    val nsUrl = NSURL.URLWithString(url) ?: run {
        scope.launch { client.publishOAuthOutcome(OAuthOutcome.Failed("malformed_authorize_url")) }
        return
    }
    val session = ASWebAuthenticationSession(
        uRL = nsUrl,
        callback = ASWebAuthenticationSessionCallback.callbackWithCustomScheme(REDIRECT_SCHEME),
        completionHandler = { callbackUrl, error ->
            activeSession = null
            scope.launch {
                when {
                    callbackUrl != null -> client.handleOAuthCallback(callbackUrl.absoluteString.orEmpty())
                    // 关掉授权页，或在系统那句「想要使用…登录」上点取消——都是主动放弃
                    error.isUserCancellation() -> client.publishOAuthOutcome(OAuthOutcome.Cancelled)
                    else -> client.publishOAuthOutcome(OAuthOutcome.Failed("asweb_${error?.code ?: -1}"))
                }
            }
        },
    )
    session.presentationContextProvider = anchorProvider
    activeSession = session
    if (!session.start()) {
        activeSession = null
        // 起不来就没有回跳，终态只能在这里补，否则漏斗里的 started 落单
        scope.launch { client.publishOAuthOutcome(OAuthOutcome.Failed("asweb_start_failed")) }
    }
}

private fun String.appendClientFlowId(flowId: String?): String =
    if (flowId.isNullOrBlank()) this
    else "$this&client_flow_id=${flowId.encodeURLParameter()}"

private fun NSError?.isUserCancellation(): Boolean =
    this != null && code == ASWebAuthenticationSessionErrorCodeCanceledLogin

/** 与登录面板同款的归因映射：能透传服务端错误码就透传（库里那份是 internal API，不给消费方用） */
private fun LoginbaseException.failureReason(): String = when (this) {
    is LoginbaseException.Api -> rawError
    is LoginbaseException.Network -> "network"
    is LoginbaseException.MalformedResponse -> "malformed_response"
    else -> "unknown"
}

private class AnchorProvider :
    NSObject(),
    ASWebAuthenticationPresentationContextProvidingProtocol {

    override fun presentationAnchorForWebAuthenticationSession(
        session: ASWebAuthenticationSession,
    ): ASPresentationAnchor = UIApplication.sharedApplication.keyWindow ?: UIWindow()
}
