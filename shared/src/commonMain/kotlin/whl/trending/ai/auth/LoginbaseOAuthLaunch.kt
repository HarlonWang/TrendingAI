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

internal fun launchGithubSignIn(client: AuthClient): Boolean =
    globalOAuthLauncher?.invoke(client, OAuthMode.SIGN_IN) ?: false

internal fun launchGithubLink(client: AuthClient): Boolean =
    globalOAuthLauncher?.invoke(client, OAuthMode.LINK) ?: false
