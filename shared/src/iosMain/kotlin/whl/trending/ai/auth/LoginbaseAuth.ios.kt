package whl.trending.ai.auth

import io.ktor.client.engine.darwin.Darwin
import wang.harlon.loginbase.NSUserDefaultsTokenStore

/**
 * iOS 侧初始化入口：把 loginbase 的类型封在 shared 内，MainViewController 调一行即可。
 *
 * 显式给 engine 而不吃 ktor 的缺省发现：Kotlin/Native 上「classpath 里有 engine」是链接期
 * 的事，缺省路径拿不到时要到发第一个请求时才炸。
 *
 * TokenStore 是 NSUserDefaults 而非 Keychain——卸载即丢会话，重装要重新登录。
 *
 * OAuth 的发起不在这里：见 [globalOAuthLauncher]，iOS 尚未注入，登录面板只有邮箱一条路。
 */
fun initLoginbaseAuth(): LoginbaseAuthManager =
    initLoginbaseAuth(NSUserDefaultsTokenStore()) { Darwin.create() }
