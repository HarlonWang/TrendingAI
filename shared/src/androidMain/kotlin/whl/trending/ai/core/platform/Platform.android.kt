package whl.trending.ai.core.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
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

actual fun openAppSettings() {
    val context = AndroidContextHolder.get() ?: return
    val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

actual fun openUrl(url: String, targetPackage: String?) {
    val context = AndroidContextHolder.get() ?: return
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    
    if (targetPackage != null) {
        intent.setPackage(targetPackage)
    }
    
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        // Fallback to system default (browser) if specified app is not installed or fails
        if (targetPackage != null) {
            intent.setPackage(null)
            context.startActivity(intent)
        }
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
