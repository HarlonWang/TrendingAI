package whl.trending.ai.core.platform

import whl.trending.ai.data.local.globalSettingsManager

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

expect fun openAppSettings()

/** 底层原语：系统浏览器直跳外链（Android ACTION_VIEW / iOS openURL）。作为 [openUrl] 的最终兜底，勿直接调用。 */
expect fun openInSystemBrowser(url: String)

/**
 * 底层原语：在系统浏览器环境中打开链接（Android Custom Tabs / iOS SFSafariViewController）。
 * 真实浏览器指纹可正常通过 Cloudflare 等人机验证，并自带翻译/密码填充/登录态。
 * 供 [openUrl] 调用，勿直接调用。
 *
 * @return 是否成功调起；false 时由 [openUrl] 兜底。
 */
expect fun openInCustomTab(url: String): Boolean

/**
 * 外链统一出口。全 app 打开外链一律调这个，优先级：
 * ① 非 http/https（mailto/tel 等）→ 交系统处理；
 * ② 用户开启「用 Custom Tab」且成功调起 → [openInCustomTab]；
 * ③ 传了 [onInAppFallback] → 应用内 WebView；否则 → [openInSystemBrowser]。
 *
 * @param onInAppFallback 应用内 WebView 兜底。组合树外（如更新弹窗）拿不到导航栈时传 null，退化为系统浏览器。
 */
fun openUrl(url: String, onInAppFallback: ((url: String) -> Unit)? = null) {
    val isWeb = url.startsWith("http://") || url.startsWith("https://")
    if (isWeb && globalSettingsManager.getOpenLinksInCustomTabSync() && openInCustomTab(url)) {
        return
    }
    if (isWeb && onInAppFallback != null) {
        onInAppFallback(url)
        return
    }
    openInSystemBrowser(url)
}

/** 调起系统分享面板，把纯文本交给用户选择的目标 App（AI App / 笔记 / IM 等）。 */
expect fun shareText(text: String)

expect fun getAppVersion(): String

expect fun isIosPlatform(): Boolean

expect fun getSystemLanguage(): String

/** 系统语言的本地化显示名（如「中文」「English」「Português」），用于展示给用户，而非语言代码。 */
expect fun getSystemLanguageDisplayName(): String

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
