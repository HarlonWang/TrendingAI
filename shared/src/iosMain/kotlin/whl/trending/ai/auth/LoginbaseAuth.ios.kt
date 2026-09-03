package whl.trending.ai.auth

import io.ktor.client.engine.darwin.Darwin
import wang.harlon.loginbase.NSUserDefaultsTokenStore
import wang.harlon.loginbase.OAuthProvider
import wang.harlon.loginbase.browser.link
import wang.harlon.loginbase.browser.signIn

/**
 * 回跳地址。**服务端 `AUTH_DEEPLINKS` 白名单必须含此值**，与 Android 的 release 变体同一个
 * （scheme 归属按平台隔离，同值不冲突）；iOS 没有 debug 变体，故不像 Android 那样从构建配置读。
 */
private const val REDIRECT_URI = "cn.trendingai:/loginbase/callback"

/**
 * iOS 侧的 loginbase 全部接线，MainViewController 调这一行即可，幂等。
 *
 * 显式给 engine 而不吃 ktor 的缺省发现：Kotlin/Native 上「classpath 里有 engine」是链接期
 * 的事，缺省路径拿不到时要到发第一个请求时才炸。
 *
 * TokenStore 是 NSUserDefaults 而非 Keychain——卸载即丢会话，重装要重新登录。
 */
fun initLoginbaseAuth(): LoginbaseAuthManager {
    val manager = initLoginbaseAuth(NSUserDefaultsTokenStore()) { Darwin.create() }
    globalOAuthLauncher = { client, mode, clientFlowId ->
        when (mode) {
            OAuthMode.SIGN_IN -> client.signIn(OAuthProvider.GitHub, REDIRECT_URI, clientFlowId)
            OAuthMode.LINK -> client.link(OAuthProvider.GitHub, REDIRECT_URI)
        }
        true
    }
    return manager
}
