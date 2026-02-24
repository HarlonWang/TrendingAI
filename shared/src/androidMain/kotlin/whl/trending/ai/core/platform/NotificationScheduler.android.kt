package whl.trending.ai.core.platform

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

actual object NotificationScheduler {
    private var permissionLauncher: ActivityResultLauncher<String>? = null
    private var onPermissionGranted: (() -> Unit)? = null

    fun initLauncher(launcher: ActivityResultLauncher<String>) {
        this.permissionLauncher = launcher
    }

    fun onPermissionResult(isGranted: Boolean) {
        if (isGranted) {
            onPermissionGranted?.invoke()
        }
        onPermissionGranted = null
    }

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
                this.onPermissionGranted = onGranted
                permissionLauncher?.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            onGranted()
        }
    }
}
