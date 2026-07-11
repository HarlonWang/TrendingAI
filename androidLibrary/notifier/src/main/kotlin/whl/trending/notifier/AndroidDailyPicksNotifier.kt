package whl.trending.notifier

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.notification.DailyPicksNotifier

/**
 * [DailyPicksNotifier] 的 Android 实现：负责通知权限申请与 WorkManager 每日任务的调度。
 * 在 MainActivity.onCreate 注入 globalDailyPicksNotifier（仿 LogtoAuthManager；
 * 必须在 onCreate 构造——RequestPermission launcher 要求在 STARTED 前注册）。
 */
class AndroidDailyPicksNotifier(private val activity: ComponentActivity) : DailyPicksNotifier {

    private var permissionResult: CompletableDeferred<Boolean>? = null
    private val permissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionResult?.complete(granted)
        permissionResult = null
    }

    override val isSupported = true

    override suspend fun enable(): Boolean {
        if (!ensurePermission()) return false
        // UPDATE：重新开启时按当前时刻重算 initialDelay，覆盖旧排期
        schedule(activity.applicationContext, ExistingPeriodicWorkPolicy.UPDATE)
        return true
    }

    override fun disable() {
        WorkManager.getInstance(activity.applicationContext).cancelUniqueWork(WORK_NAME)
    }

    private suspend fun ensurePermission(): Boolean {
        if (NotificationManagerCompat.from(activity).areNotificationsEnabled()) return true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // 13 以下没有运行时权限，通知被系统级关闭只能引导用户去系统设置
            return false
        }
        val deferred = CompletableDeferred<Boolean>()
        permissionResult = deferred
        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        return deferred.await()
    }

    companion object {
        private const val WORK_NAME = "daily_picks_notification"
        private const val NOTIFY_HOUR = 9

        /**
         * 冷启动对账：开关为开时确保周期任务仍在（KEEP 不动既有排期）。
         * WorkManager 本身跨重启持久，这里兜底「清数据后恢复备份 / 系统清理任务」等脱节场景。
         */
        fun syncOnAppStart(context: Context) {
            if (globalSettingsManager.currentDailyPicksNotificationEnabled()) {
                schedule(context.applicationContext, ExistingPeriodicWorkPolicy.KEEP)
            }
        }

        private fun schedule(context: Context, policy: ExistingPeriodicWorkPolicy) {
            val request = PeriodicWorkRequestBuilder<DailyPicksWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(
                    initialDelayMillis(ZonedDateTime.now(), NOTIFY_HOUR),
                    TimeUnit.MILLISECONDS,
                )
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, policy, request)
        }
    }
}
