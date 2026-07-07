package whl.trending.ai.core.platform

import platform.UIKit.UIDevice
import platform.UIKit.UIApplication
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.Foundation.NSURL
import platform.Foundation.NSBundle
import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages
import platform.Foundation.currentLocale
import platform.Foundation.localizedStringForLanguageCode
import platform.SafariServices.SFSafariViewController

class IOSPlatform: Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
}

actual fun getPlatform(): Platform = IOSPlatform()

actual fun openAppSettings() {
    val url = NSURL(string = UIApplicationOpenSettingsURLString)
    val app = UIApplication.sharedApplication
    if (app.canOpenURL(url)) {
        app.openURL(
            url,
            options = emptyMap<Any?, Any?>(),
            completionHandler = { _ -> }
        )
    }
}

actual fun openInSystemBrowser(url: String) {
    val nsUrl = NSURL.URLWithString(url)
    if (nsUrl != null) {
        UIApplication.sharedApplication.openURL(
            nsUrl,
            options = emptyMap<Any?, Any?>(),
            completionHandler = { _ -> }
        )
    }
}

actual fun openInCustomTab(url: String): Boolean {
    // SFSafariViewController 仅支持 http/https，其余情况返回 false 交由调用方兜底
    val nsUrl = NSURL.URLWithString(url) ?: return false
    if (nsUrl.scheme != "http" && nsUrl.scheme != "https") {
        return false
    }
    var topVc = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (topVc?.presentedViewController != null) {
        topVc = topVc.presentedViewController
    }
    if (topVc == null) {
        return false
    }
    topVc.presentViewController(SFSafariViewController(uRL = nsUrl), animated = true, completion = null)
    return true
}

actual fun shareText(text: String) {
    // 最小实现：iPhone 走全屏分享 sheet。
    // TODO(iOS 真机验证): iPad 需为 popoverPresentationController 设置 sourceView/sourceRect 锚点，否则 present 会崩溃。
    val rootVc = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return
    val activityVc = UIActivityViewController(
        activityItems = listOf(text),
        applicationActivities = null
    )
    rootVc.presentViewController(activityVc, animated = true, completion = null)
}

actual fun getAppVersion(): String {
    val info = NSBundle.mainBundle.infoDictionary
    return info?.get("CFBundleShortVersionString") as? String ?: "1.0.0"
}

actual fun isIosPlatform(): Boolean = true

actual fun getSystemLanguage(): String {
    val preferredLanguage = NSLocale.preferredLanguages.firstOrNull() as? String ?: "en"
    return preferredLanguage.split("-").firstOrNull() ?: "en"
}

actual fun getSystemLanguageDisplayName(): String {
    val code = getSystemLanguage()
    val name = NSLocale.currentLocale.localizedStringForLanguageCode(code)
    return (name ?: code).replaceFirstChar { it.uppercase() }
}

internal actual fun platformTrackEvent(name: String, props: Map<String, Any>) {
    // iOS 暂不接入事件上报
}

actual fun getUserAgent(): String {
    val appVersion = getAppVersion()
    val device = UIDevice.currentDevice
    val osVersion = device.systemVersion
    val model = device.model
    val channel = ChannelHolder.get()
    return "TrendingAI/$appVersion (iOS $osVersion; $model; channel=$channel)"
}
