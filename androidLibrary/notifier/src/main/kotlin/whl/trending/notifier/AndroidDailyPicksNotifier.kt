package whl.trending.notifier

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.work.BackoffPolicy
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
        // 覆盖前先了结旧请求，避免快速连续开关时前一个协程永远挂在 await 上
        permissionResult?.complete(false)
        val deferred = CompletableDeferred<Boolean>()
        permissionResult = deferred
        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        return deferred.await()
    }

    companion object {
        private const val WORK_NAME = "daily_picks_notification"

        // 本地 09:30：服务端 Picks 于 UTC 01:00（北京 09:00）才开始生成、Newsletter 排在
        // UTC 01:15 发送（即后端合同为 15 分钟内出结果）。取 09:30 让 UTC+8 主力用户
        // 一次命中新内容，避免每天固定吃一轮 30 分钟重试；其他时区不受影响
        // （西侧触发时内容早已就绪，东侧仍靠重试梯子兜底）。
        private const val NOTIFY_HOUR = 9
        private const val NOTIFY_MINUTE = 30

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
                    initialDelayMillis(ZonedDateTime.now(), NOTIFY_HOUR, NOTIFY_MINUTE),
                    TimeUnit.MILLISECONDS,
                )
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                // 「内容未更新」的 retry 用线性 30 分钟退避：默认指数退避从 30s 起，
                // 5 次尝试 10 分钟内烧完；线性能把重试摊到 9:30/10:30/12:00/14:00，
                // 覆盖服务端上午晚更新的情况
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, policy, request)
        }
    }
}
