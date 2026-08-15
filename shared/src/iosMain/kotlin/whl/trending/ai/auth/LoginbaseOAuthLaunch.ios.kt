package whl.trending.ai.auth

import wang.harlon.loginbase.AuthClient

// iOS 的 globalAuthManager 是 Noop（登录不可用），这两个占位不可达
internal actual fun launchGithubSignIn(client: AuthClient): Boolean = false

internal actual fun launchGithubLink(client: AuthClient): Boolean = false
