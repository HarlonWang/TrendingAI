package whl.trending.ai

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import whl.trending.ai.auth.OauthCallbackBus

/**
 * OAuth 回跳的接收 Activity（透明、一闪即逝）。
 *
 * **为什么不是让 MainActivity 直接接**：默认 `standard` 启动模式下，浏览器发起的
 * deepLink 会在浏览器自己的 task 里新建一个 MainActivity 实例——用户看到「跳回首页」，
 * 而登录面板不在那个新实例上，otc 投到总线后无人消费。
 *
 * **为什么不是给 MainActivity 加 singleTask**：那能解决问题，但把 OAuth 的特殊需求
 * 写进了 App 的全局启动语义；且 AppAuth 记录过 singleTask 的两个坑——多进程 +
 * taskAffinity 难管理（issue #170）、Android 14 上实例被重建而非复用（issue #977，
 * 他们改用了 singleInstance）。本 Activity 沿用 AppAuth 的
 * `RedirectUriReceiverActivity` 结构：**自己保持默认 standard**，只做三件事——
 * 投递结果、把主 Activity 带回前台、结束自己。
 *
 * 结果通过进程级的 [OauthCallbackBus] 传递，不依赖 Activity 实例，所以「进程已被杀、
 * 从回跳冷启动」也能工作：emit 先于收集者建立，`replay = 1` 保证事件不丢。
 */
class AuthRedirectActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        intent?.data?.toString()
            ?.let { OauthCallbackBus.parse(it) }
            ?.let { OauthCallbackBus.emit(it) }

        // 把主 Activity 带回前台：透明 Activity 直接 finish 的话，用户会留在浏览器里
        // （这一步正是 AppAuth 用显式 startActivity 解决的）。REORDER_TO_FRONT 复用
        // 已有实例；进程被杀时它不存在，则按普通启动新建，事件由 replay 兜住。
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        )
        finish()
    }
}
