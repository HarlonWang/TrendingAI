package whl.trending.ai.core.platform

import platform.UIKit.UIDevice
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.Foundation.NSURL
import platform.Foundation.NSBundle
import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages

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

actual fun openUrl(url: String, targetPackage: String?) {
    val nsUrl = NSURL.URLWithString(url)
    if (nsUrl != null) {
        UIApplication.sharedApplication.openURL(
            nsUrl,
            options = emptyMap<Any?, Any?>(),
            completionHandler = { _ -> }
        )
    }
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

internal actual fun platformTrackEvent(name: String, props: Map<String, Any>) {
    // iOS 暂不接入事件上报
}

actual fun getUserAgent(): String {
    val appVersion = getAppVersion()
    val device = UIDevice.currentDevice
    val osVersion = device.systemVersion
    val model = device.model
    return "TrendingAI/$appVersion (iOS $osVersion; $model)"
}
