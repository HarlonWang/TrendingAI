package whl.trending.ai.core.platform

import whl.trending.ai.data.local.globalSettingsManager

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

expect fun openAppSettings()

expect fun openUrl(url: String, targetPackage: String? = null)

/**
 * 在系统浏览器环境中打开链接（Android Custom Tabs / iOS SFSafariViewController），
 * 用于应用内 WebView 无法通过 Cloudflare 人机验证的站点，见 [isCloudflareProtectedUrl]。
 */
expect fun openInCustomTab(url: String)

/** 调起系统分享面板，把纯文本交给用户选择的目标 App（AI App / 笔记 / IM 等）。 */
expect fun shareText(text: String)

expect fun getAppVersion(): String

expect fun isIosPlatform(): Boolean

expect fun getSystemLanguage(): String

expect fun getUserAgent(): String

/**
 * 统一埋点入口：自动为每个事件附带稳定的安装级 install_id，再交由平台上报。
 *
 * Aptabase 默认的 user_id 由「每日轮换 salt」哈希生成，无法跨天追踪同一用户，
 * 留存分析因此失效。这里补一个卸载重装才会变的 install_id，
 * 留存/回访改以 install_id 为口径（安装日 = 该 install_id 首次出现的日期）。
 */
fun trackEvent(name: String, props: Map<String, Any> = emptyMap()) {
    platformTrackEvent(
        name,
        props + mapOf(
            "install_id" to globalSettingsManager.getOrCreateInstallId(),
            "channel" to ChannelHolder.get()
        )
    )
}

/** 平台真正的事件上报实现（Android 接 Aptabase，iOS 暂为空）。 */
internal expect fun platformTrackEvent(name: String, props: Map<String, Any>)

/**
 * 条目点击埋点便捷封装，上报 "item_click" 事件。
 * @param source 数据来源：github / hackernews / producthunt
 * @param rank 条目在列表中的排名（从 1 开始）
 * @param title 条目标题，截断至 100 字符
 * @param section 仅 Picks 页使用：deep_dive / controversy / speed_read
 */
fun trackItemClick(
    source: String,
    rank: Int,
    title: String,
    section: String? = null
) {
    val props = mutableMapOf<String, Any>(
        "source" to source,
        "rank" to rank,
        "title" to title.take(100)
    )
    section?.let { props["section"] = it }
    trackEvent("item_click", props)
}
