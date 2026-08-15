package whl.trending.ai.auth

import wang.harlon.loginbase.AuthClient

/**
 * 拉起 GitHub OAuth 授权。浏览器环节（中转页/管理页/CCT 探测/取消判定/进程回收兜底）
 * 整体归 `loginbase-kt-browser`（Android-only），结果只从 [AuthClient.oauthResults] 送达。
 *
 * iOS 上 [globalAuthManager] 是 Noop、登录面板里的 GitHub 路径不可达，actual 只是
 * 编译占位（返回 false）。
 *
 * @return false = 平台不支持或宿主 Activity 不在（防御路径，正常流程不该走到）
 */
internal expect fun launchGithubSignIn(client: AuthClient): Boolean

/** 同 [launchGithubSignIn]，绑定流程。授权 URL 的换取（带 Bearer 的 link/start）在库内完成。 */
internal expect fun launchGithubLink(client: AuthClient): Boolean
