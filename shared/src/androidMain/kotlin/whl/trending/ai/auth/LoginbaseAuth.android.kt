package whl.trending.ai.auth

import android.content.Context
import wang.harlon.loginbase.SharedPreferencesTokenStore

/**
 * Android 侧初始化入口：把 loginbase 的类型封在 shared 内，androidApp 只需给一个 Context。
 *
 * TokenStore 用库的 SharedPreferences 实现——它**同步 commit 落盘**，是与服务端
 * 「丢回执救活」配套的硬要求（救活有 1h/3 次护栏）。别改用 App 自己那份 `Settings`：
 * multiplatform-settings 默认走异步 apply，进程被杀会丢掉刚轮换到的令牌。
 */
fun initLoginbaseAuth(context: Context): LoginbaseAuthManager =
    initLoginbaseAuth(SharedPreferencesTokenStore(context.applicationContext))
