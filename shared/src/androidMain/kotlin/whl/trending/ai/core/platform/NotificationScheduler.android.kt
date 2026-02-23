package whl.trending.ai.core.platform

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

actual object NotificationScheduler {
    actual fun update(enabled: Boolean) {
        val context = AndroidContextHolder.get() ?: return
        val workManager = WorkManager.getInstance(context)

        if (enabled) {
            // Cancel existing work to avoid duplicates
            workManager.cancelAllWorkByTag("DailyReminder")

            // Schedule the first one
            val delay = DailyReminderWorker.calculateNextDelay()
            val firstRequest = OneTimeWorkRequestBuilder<DailyReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .addTag("DailyReminder")
                .build()

            workManager.enqueue(firstRequest)
        } else {
            workManager.cancelAllWorkByTag("DailyReminder")
        }
    }

    actual fun requestPermission(onGranted: () -> Unit) {
        val context = AndroidContextHolder.get() ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                onGranted()
            } else {
                // For Android 13+, the actual request usually needs an Activity.
                // We'll let the user handle the permission request in MainActivity if needed,
                // or just call onGranted and let the system handle it (it won't show if no permission).
                // A better way would be to use a platform-specific Activity provider.
                onGranted() 
            }
        } else {
            onGranted()
        }
    }
}
