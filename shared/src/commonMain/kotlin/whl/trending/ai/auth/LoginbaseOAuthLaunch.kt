package whl.trending.ai.auth

import wang.harlon.loginbase.AuthClient

enum class OAuthMode { SIGN_IN, LINK }

/**
 * GitHub OAuth 发起的注入点（仿 globalChatScreen 的依赖反转）。
 *
 * 浏览器环节归 loginbase-kt-browser（Android-only），**只由 androidApp 依赖并在
 * MainActivity 注入**——shared 不碰它：它的 manifest（含 placeholder）会合并进
 * 所有依赖方模块的单测 manifest，直接依赖会让 shared/chat 的 test 任务构建失败。
 * iOS 不注入（globalAuthManager 是 Noop，此路径不可达）。
 */
var globalOAuthLauncher: ((AuthClient, OAuthMode) -> Boolean)? = null

/**
 * OAuth 回跳结果的**重复消费闸**。
 *
 * `client.oauthResults` 是 `replay = 1` 的 SharedFlow，消费者
 * [whl.trending.ai.ui.common.OAuthOutcomeHost] 挂在 composition 上。从自定义标签页回跳会重建
 * Activity（实测：backStack 回落首页），重建期间新旧两个 composition 短暂并存、同时订阅，
 * **同一次投递被消费两次**：1.4.0 首日的 `auth_finished(canceled)` 因此全是成对的，
 * 登录完成率算出 200%（4 条 started 对 8 条 finished）；`consumePendingSource()` 这类
 * 一次性读取也会被跑两遍。
 *
 * 判据是「同一次投递」而不是「同一个 flow」：同一条 flow 内用户可以真的取消两次
 * （生产数据里就有，23:33:11 与 23:33:16 各一次），按 flow 去重会把第二次真实取消吞掉。
 * 靠**每次发起授权时重置**做到这一点——`Cancelled` 是 object 单例，跨轮次的引用相等
 * 不能说明什么，重置后才可信。
 */
internal object OAuthResultGuard {
    private var lastHandled: Any? = null

    /** 发起新一轮授权，上一轮的结果不再算「已处理」。 */
    fun reset() {
        lastHandled = null
    }

    /** 本轮内首次见到这条结果才为 true。只在 Compose 主线程调用。 */
    fun shouldHandle(outcome: Any): Boolean {
        if (lastHandled === outcome) return false
        lastHandled = outcome
        return true
    }
}

internal fun launchGithubSignIn(client: AuthClient): Boolean {
    OAuthResultGuard.reset()
    return globalOAuthLauncher?.invoke(client, OAuthMode.SIGN_IN) ?: false
}

internal fun launchGithubLink(client: AuthClient): Boolean {
    OAuthResultGuard.reset()
    return globalOAuthLauncher?.invoke(client, OAuthMode.LINK) ?: false
}
