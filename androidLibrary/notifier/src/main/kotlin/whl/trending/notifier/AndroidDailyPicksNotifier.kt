package whl.trending.notifier

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkManager
import kotlinx.coroutines.CompletableDeferred
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.notification.DailyPicksNotifier

/**
 * [DailyPicksNotifier] 的 Android 实现：负责通知权限申请与每日闹钟的调度。
 * 在 MainActivity.onCreate 注入 globalDailyPicksNotifier（仿 LogtoAuthManager；
 * 必须在 onCreate 构造——RequestPermission launcher 要求在 STARTED 前注册）。
 * 调度细节见 [DailyPicksAlarmScheduler]。
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
        // 重新开启时按当前时刻重排，覆盖旧排期
        DailyPicksAlarmScheduler.scheduleNextDay(activity.applicationContext)
        return true
    }

    override fun disable() {
        DailyPicksAlarmScheduler.cancel(activity.applicationContext)
    }

    private suspend fun ensurePermission(): Boolean {
        if (NotificationManagerCompat.from(activity).areNotificationsEnabled()) return true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // 13 以下没有运行时权限，通知被系统级关闭只能引导用户去系统设置
            return false
        }
        // 覆盖前先了结旧请求，避免快速连续开关时前一个协程永远挂在 await 上
        permissionResult?.complete(false)
        val deferred = CompletableDeferred<Boolean>()
        permissionResult = deferred
        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        return deferred.await()
    }

    companion object {
        // 闹钟方案之前的 WorkManager 排期：v1 周期任务与 v2 自续期一次性链
        private const val LEGACY_PERIODIC_WORK_NAME = "daily_picks_notification"
        private const val LEGACY_CHAIN_WORK_NAME = "daily_picks_notification_v2"

        /**
         * 冷启动对账：清掉迁移前的 WorkManager 排期（androidx.work 依赖仅为这两行保留，
         * 观察几个版本后连依赖一起删），开关为开时核对闹钟链是否完好。
         */
        fun syncOnAppStart(context: Context) {
            val appContext = context.applicationContext
            WorkManager.getInstance(appContext).apply {
                cancelUniqueWork(LEGACY_PERIODIC_WORK_NAME)
                cancelUniqueWork(LEGACY_CHAIN_WORK_NAME)
            }
            if (globalSettingsManager.currentDailyPicksNotificationEnabled()) {
                DailyPicksAlarmScheduler.reconcile(appContext)
            }
        }
    }
}
