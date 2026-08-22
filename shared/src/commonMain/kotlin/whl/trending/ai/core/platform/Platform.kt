package whl.trending.ai.core.platform

import whl.trending.ai.data.local.AppIconPreset
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
    // ignoreCase：scheme 大小写不敏感（RFC 3986）。大写 scheme 若被判非 web，会绕过
    // Custom Tab 与 WebView 兜底直落 ACTION_VIEW，而 intent filter 按小写匹配 → 点击静默无响应
    val isWeb = url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)
    if (isWeb && globalSettingsManager.currentOpenLinksInCustomTab() && openInCustomTab(url)) {
        return
    }
    if (isWeb && onInAppFallback != null) {
        onInAppFallback(url)
        return
    }
    openInSystemBrowser(url)
}

/**
 * 文件直链下载统一出口（如 APK）：跳过 Custom Tab / 应用内 WebView，直接交系统浏览器接管下载。
 * 直链文件在 Custom Tab 里体验糟糕——Chrome 下载后留一个空白页挂在前台，其他 provider 行为不定。
 *
 * 约定仅用于 http(s) 直链；非 web scheme 请走 [openUrl]（两者对非 web 最终都是同一条
 * [openInSystemBrowser] 路径，行为一致，此约定只为语义清晰）。
 */
fun openDownloadUrl(url: String) {
    openInSystemBrowser(url)
}

/** 调起系统分享面板，把纯文本交给用户选择的目标 App（AI App / 笔记 / IM 等）。 */
expect fun shareText(text: String)

/**
 * 取不到版本号时的兜底值。**必须是解析不出数值段的字符串**：
 *
 * - [whl.trending.ai.update.isVersionBlocked] 靠「任一侧解析失败即不拦截」保证兜底值不会把用户
 *   锁死在强更页，而旧兜底值 `"1.0.0"` 能被正常解析——`isVersionBlocked("1.0.0", "1.4.0")` 返回 true，
 *   那条保护一直形同虚设；
 * - 它同时是个显眼的哨兵：埋点里看到 `unknown` 就知道取版本号的时机不对，而 `"1.0.0"` 会伪装成
 *   真实版本悄悄污染版本切片（2026-08-21 首发当天就这么发生了，见父仓 eventbase-首发读数-2026-08-22.md）。
 */
const val UNKNOWN_APP_VERSION: String = "unknown"

expect fun getAppVersion(): String

/** 是否支持切换桌面图标（Android activity-alias 机制）。false 时外观页隐藏「应用图标」整块。 */
expect fun supportsAlternateAppIcons(): Boolean

/**
 * 切换桌面图标：启用 [preset] 对应的 launcher alias、禁用其余。
 * 立即生效（`DONT_KILL_APP`），不支持的平台为空操作。
 */
expect fun applyAppIcon(preset: AppIconPreset)

expect fun isIosPlatform(): Boolean

expect fun getSystemLanguage(): String

/** 系统语言的本地化显示名（如「中文」「English」「Português」），用于展示给用户，而非语言代码。 */
expect fun getSystemLanguageDisplayName(): String

/**
 * 系统级完整 locale 标签（BCP-47，如 "zh-Hant-TW" / "de-DE"），用于埋点。
 * 必须读系统配置而非 [getSystemLanguage] 的 Locale.getDefault()——应用内切换语言
 * （setApplicationLocales）会覆盖后者，导致上报的是 app 语言而非用户真实设备语言。
 */
expect fun getSystemLocaleTag(): String

expect fun getUserAgent(): String

