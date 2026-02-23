package whl.trending.ai.core.platform

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.getString
import trending.shared.generated.resources.Res
import trending.shared.generated.resources.notification_content
import trending.shared.generated.resources.notification_title
import java.util.concurrent.TimeUnit
import kotlin.time.Clock

class DailyReminderWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        showNotification()
        scheduleNext()
        return Result.success()
    }

    private suspend fun showNotification() {
        val context = applicationContext
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "daily_reminder_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Daily Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val title = getString(Res.string.notification_title)
        val content = getString(Res.string.notification_content)

        // Intent to open the app
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Placeholder icon
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1, notification)
    }

    private fun scheduleNext() {
        val delay = calculateNextDelay()
        val nextRequest = OneTimeWorkRequestBuilder<DailyReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag("DailyReminder")
            .build()

        WorkManager.getInstance(applicationContext).enqueue(nextRequest)
    }

    companion object {
        fun calculateNextDelay(): Long {
            val now = Clock.System.now()
            val timeZone = TimeZone.UTC
            val today = now.toLocalDateTime(timeZone)
            
            val targets = listOf(
                today.date.atTime(0, 30),
                today.date.atTime(12, 30),
                today.date.plus(1, DateTimeUnit.DAY).atTime(0, 30),
                today.date.plus(1, DateTimeUnit.DAY).atTime(12, 30)
            )
            
            val nextTarget = targets.first { it.toInstant(timeZone) > now }
            return (nextTarget.toInstant(timeZone) - now).inWholeMilliseconds
        }
    }
}
