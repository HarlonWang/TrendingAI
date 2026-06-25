package whl.trending.ai

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.aptabase.Aptabase
import whl.trending.ai.core.platform.ChannelHolder
import whl.trending.ai.core.platform.trackEvent
import whl.trending.chat.engine.byok.SecureKeyStore

class TrendingApplication : Application(), DefaultLifecycleObserver {
    private var sessionStartTime: Long = 0

    override fun onCreate() {
        super<Application>.onCreate()
        // 写入分发渠道（须在任何 trackEvent / 网络请求之前），供 shared 埋点与 UA 统一打标
        ChannelHolder.set(BuildConfig.CHANNEL)

        // 初始化 BYOK apiKey 的加密存储（须在聊天/设置读取 key 之前）
        SecureKeyStore.init(this)

        // Initialize Aptabase with the provided App Key
        Aptabase.instance.initialize(this, "A-US-1808698868")

        // Track app launch（经 shared trackEvent 统一注入 install_id + channel）
        trackEvent("app_started")

        // Register lifecycle observer
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
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
