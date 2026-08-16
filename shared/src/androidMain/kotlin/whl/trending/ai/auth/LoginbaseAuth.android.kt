package whl.trending.ai.auth

import android.content.Context
import wang.harlon.loginbase.SharedPreferencesTokenStore

/**
 * Android 侧初始化入口：把 loginbase 的类型封在 shared 内，androidApp 只需给一个 Context。
 *
 * TokenStore 用库的 SharedPreferences 实现——它**同步 commit 落盘**，是与服务端
 * 「丢回执救活」配套的硬要求。别改用 App 自己那份 `Settings`（异步 apply，
 * 进程被杀会丢刚轮换的令牌）。
 *
 * OAuth 的发起不在这里：见 [globalOAuthLauncher]（browser 模块只由 androidApp 依赖）。
 */
fun initLoginbaseAuth(context: Context): LoginbaseAuthManager =
    initLoginbaseAuth(SharedPreferencesTokenStore(context.applicationContext))
