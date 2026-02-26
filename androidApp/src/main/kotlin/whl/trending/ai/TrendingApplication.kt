package whl.trending.ai

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.aptabase.Aptabase

class TrendingApplication : Application(), DefaultLifecycleObserver {
    private var sessionStartTime: Long = 0

    override fun onCreate() {
        super<Application>.onCreate()
        // Initialize Aptabase with the provided App Key
        Aptabase.instance.initialize(this, "A-US-1808698868")
        
        // Track app launch
        Aptabase.instance.trackEvent("app_started")

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
                Aptabase.instance.trackEvent("app_session", mapOf(
                    "duration" to durationSeconds.toInt()
                ))
            }
            sessionStartTime = 0
        }
    }
}
