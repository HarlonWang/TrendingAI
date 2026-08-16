package whl.trending.ai.core.platform

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import whl.trending.ai.data.local.AppIconPreset
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import java.lang.ref.WeakReference

object AndroidContextHolder {
    private var contextRef: WeakReference<Context>? = null

    fun initialize(context: Context) {
        contextRef = WeakReference(context.applicationContext)
    }

    fun get(): Context? = contextRef?.get()
}

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
}

actual fun getPlatform(): Platform = AndroidPlatform()

actual fun supportsAlternateAppIcons(): Boolean = true

/**
 * alias 类名固定用 namespace 全限定，不能拿 `context.packageName` 拼：
 * debug 构建有 applicationIdSuffix ".debug"，而 alias 的类名始终按 namespace（whl.trending.ai）解析，
 * 拼 packageName 会得到 whl.trending.ai.debug.MainActivityXxx 这种不存在的组件。
 */
private fun AppIconPreset.aliasClassName(): String = "whl.trending.ai.MainActivity" + when (this) {
    AppIconPreset.DEFAULT -> "Default"
    AppIconPreset.GRAPHITE -> "Graphite"
    AppIconPreset.STEEL -> "Steel"
    AppIconPreset.PINE -> "Pine"
    AppIconPreset.BERRY -> "Berry"
    AppIconPreset.CREAM -> "Cream"
}

// 参照 SmokingYou（GPL-3.0，仅参照思路重写）的 AppIconManager：
// 遍历全部 launcher alias，启用目标、禁用其余，任何时刻恰好一个桌面入口
actual fun applyAppIcon(preset: AppIconPreset) {
    val context = AndroidContextHolder.get() ?: return
    val pm = context.packageManager
    AppIconPreset.entries.forEach { candidate ->
        val state = if (candidate == preset) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        try {
            pm.setComponentEnabledSetting(
                ComponentName(context.packageName, candidate.aliasClassName()),
                state,
                PackageManager.DONT_KILL_APP,
            )
        } catch (e: Exception) {
            // 单个 alias 失败不阻断其余：宁可先把目标启用，也别让桌面同时挂两个图标
            Log.w("Platform", "applyAppIcon failed for ${candidate.id}", e)
        }
    }
}

actual fun openAppSettings() {
    val context = AndroidContextHolder.get() ?: return
    val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

actual fun openInSystemBrowser(url: String) {
    val context = AndroidContextHolder.get() ?: return
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        // 设备无浏览器可承接，静默
        Log.w("Platform", "openInSystemBrowser failed", e)
    }
}

actual fun openInCustomTab(url: String): Boolean {
    val context = AndroidContextHolder.get() ?: return false
    return try {
        val customTabsIntent = CustomTabsIntent.Builder().build()
        customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // GitHub Sponsors 页依赖 github.com 的浏览器登录态，但 github.com 已被本机 GitHub 客户端
        // 的 verified App Links 认领：无包名的 Custom Tab intent（底层 ACTION_VIEW）会被它优先截走，
        // 而它打不开 Sponsors 页又会再弹一层 chooser。仅对赞助页锁定 Custom Tabs 提供方（默认浏览器）
        // 包名绕开，确保稳定进浏览器；其余外链（含普通 github.com 仓库页）不受影响，保持系统默认解析。
        if ("github.com/sponsors" in url) {
            CustomTabsClient.getPackageName(context, null)?.let { customTabsIntent.intent.setPackage(it) }
        }
        customTabsIntent.launchUrl(context, Uri.parse(url))
        true
    } catch (e: ActivityNotFoundException) {
        // 设备上没有任何浏览器可承接，返回 false 交由调用方兜底
        Log.w("Platform", "Custom Tabs unavailable", e)
        false
    }
}

actual fun shareText(text: String) {
    val context = AndroidContextHolder.get() ?: return
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(sendIntent, null).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
}

actual fun getAppVersion(): String {
    val context = AndroidContextHolder.get() ?: return "1.0.0"
    return try {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        packageInfo.versionName ?: "1.0.0"
    } catch (e: Exception) {
        "1.0.0"
    }
}

actual fun isIosPlatform(): Boolean = false

actual fun getSystemLanguage(): String = java.util.Locale.getDefault().language

actual fun getSystemLanguageDisplayName(): String {
    val locale = java.util.Locale.getDefault()
    return locale.getDisplayLanguage(locale).replaceFirstChar { it.uppercase() }
}

actual fun getSystemLocaleTag(): String =
    android.content.res.Resources.getSystem().configuration.locales[0].toLanguageTag()

internal actual fun platformTrackEvent(name: String, props: Map<String, Any>) {
    com.aptabase.Aptabase.instance.trackEvent(name, props)
}

actual fun getUserAgent(): String {
    val appVersion = getAppVersion()
    val osVersion = Build.VERSION.RELEASE
    val model = Build.MODEL
    val channel = ChannelHolder.get()
    return "TrendingAI/$appVersion (Android $osVersion; $model; channel=$channel)"
}
