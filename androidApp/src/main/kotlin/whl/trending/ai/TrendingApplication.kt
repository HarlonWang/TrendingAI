package whl.trending.ai

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.aptabase.Aptabase
import whl.trending.ai.core.platform.ChannelHolder
import whl.trending.ai.core.platform.trackEvent

class TrendingApplication : Application(), DefaultLifecycleObserver {
    private var sessionStartTime: Long = 0

    /** 每进程只报一次 app_started，前后台来回切不重复计 */
    private var startedReported = false

    override fun onCreate() {
        super<Application>.onCreate()
        // 写入分发渠道（须在任何 trackEvent / 网络请求之前），供 shared 埋点与 UA 统一打标
        ChannelHolder.set(BuildConfig.CHANNEL)

        // 只初始化，不上报——初始化本身不产生事件
        Aptabase.instance.initialize(this, "A-US-1808698868")

        // Register lifecycle observer
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        // 挂首次进前台而非 onCreate：后台唤醒建的进程没有界面，在那里上报只会造出一个
        // 空 session（Aptabase 的 session 由事件构成，发出即成立）。口径见 analytics-notes
        if (!startedReported) {
            startedReported = true
            trackEvent("app_started")
        }

        // Record start time when app comes to foreground
        sessionStartTime = System.currentTimeMillis()
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        // Calculate duration and track event when app goes to background
        if (sessionStartTime > 0) {
            val durationSeconds = (System.currentTimeMillis() - sessionStartTime) / 1000
            if (durationSeconds > 0) {
                trackEvent("app_session", mapOf(
                    "duration" to durationSeconds.toInt()
                ))
            }
            sessionStartTime = 0
        }
    }
}
