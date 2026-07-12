package whl.trending.notifier

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.util.Locale
import whl.trending.ai.core.platform.trackEvent
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.data.repository.TrendingRepository

/** 通知点击深链约定：androidApp 的 MainActivity 读取该 extra 切换到 Picks tab */
const val EXTRA_OPEN_TAB = "open_tab"
const val TAB_PICKS = "picks"

/**
 * 每日 Picks 通知任务：拉取当日精选，内容为新的一天时弹本地通知。
 * 纯 androidx.work，不依赖 GMS/FCM，F-Droid 等无 Play 服务设备同样可用。
 *
 * 拉取失败或服务端尚未更新到新一天时 retry（指数退避），最多 [MAX_ATTEMPTS] 次后
 * 放弃本周期，静默等下一天——通知宁缺勿错，不拿旧内容打扰用户。
 */
class DailyPicksWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = globalSettingsManager
        // 兜底：任务与开关状态脱节（如取消失败）时以开关为准
        if (!settings.currentDailyPicksNotificationEnabled()) return Result.success()

        val lang = settings.currentContentLang()
        val picks = try {
            TrendingRepository.shared.getPicks(lang)
        } catch (e: Exception) {
            return retryOrGiveUp()
        }
        val items = picks.deepDive + picks.controversy + picks.speedRead
        if (items.isEmpty()) return retryOrGiveUp()

        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!shouldNotify(prefs.getString(KEY_LAST_NOTIFIED_DATE, null), picks.metadata.date)) {
            return retryOrGiveUp()
        }

        if (!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) {
            // 权限被系统侧收回：静默跳过，等用户重新授权
            return Result.success()
        }

        postNotification(firstTitle = items.first().title, total = items.size, lang = lang)
        prefs.edit { putString(KEY_LAST_NOTIFIED_DATE, picks.metadata.date) }
        trackEvent("daily_picks_notification_shown", mapOf("date" to picks.metadata.date))
        return Result.success()
    }

    private fun retryOrGiveUp(): Result =
        if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()

    private fun postNotification(firstTitle: String, total: Int, lang: String) {
        val context = applicationContext
        // 通知文案跟随 app 内容语言（与摘要/订阅同口径），而非仅系统语言：
        // API 33 以下 AppCompat 的应用内语言不作用于 worker 的 context，需手动定位
        val res = localizedContext(context, lang)

        val manager = NotificationManagerCompat.from(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                res.getString(R.string.daily_picks_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = res.getString(R.string.daily_picks_channel_desc) }
            manager.createNotificationChannel(channel)
        }

        // 经 launch intent 打开（notifier 不感知 MainActivity 类名，保持模块解耦）
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.apply {
                putExtra(EXTRA_OPEN_TAB, TAB_PICKS)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            } ?: return
        val pendingIntent = PendingIntent.getActivity(
            context, 0, launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val text = if (total > 1) {
            res.getString(R.string.daily_picks_notification_text, firstTitle, total - 1)
        } else {
            res.getString(R.string.daily_picks_notification_text_single, firstTitle)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_picks)
            .setContentTitle(res.getString(R.string.daily_picks_notification_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun localizedContext(context: Context, lang: String): Context {
        val config = Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(lang))
        }
        return context.createConfigurationContext(config)
    }

    companion object {
        private const val MAX_ATTEMPTS = 5
        private const val PREFS_NAME = "daily_picks_notifier"
        private const val KEY_LAST_NOTIFIED_DATE = "last_notified_date"
        private const val CHANNEL_ID = "daily_picks"
        private const val NOTIFICATION_ID = 0x9101
    }
}
