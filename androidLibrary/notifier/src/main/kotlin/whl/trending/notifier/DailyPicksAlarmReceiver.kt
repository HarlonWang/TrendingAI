package whl.trending.notifier

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import wang.harlon.eventbase.Eventbase
import whl.trending.ai.core.analytics.AppEvent
import whl.trending.ai.core.analytics.NotificationKind
import whl.trending.ai.core.analytics.NotificationStep
import whl.trending.ai.core.analytics.track
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.data.repository.TrendingRepository

/** 通知点击深链约定：androidApp 的 MainActivity 读取该 extra 切换到 Picks tab */
const val EXTRA_OPEN_TAB = "open_tab"
const val TAB_PICKS = "picks"

/** 通知携带的当天 Picks 日期，MainActivity 用它对 open 埋点去重 */
const val EXTRA_NOTIFIED_DATE = "notified_date"

/** open 埋点是否上报：true 表示本次点击是当天首次（详见 [DailyPicksPrefs.consumeOpen]） */
fun consumeDailyPicksNotificationOpen(context: Context, intent: Intent): Boolean =
    DailyPicksPrefs.consumeOpen(context, intent.getStringExtra(EXTRA_NOTIFIED_DATE))

/**
 * 每日 Picks 通知任务：拉取当日精选，内容为新的一天时弹本地通知。
 * 纯 AlarmManager + Receiver，不依赖 GMS/FCM，F-Droid 等无 Play 服务设备同样可用。
 *
 * 拉取失败或服务端尚未更新到新一天时走 +30 分钟线性重试梯子，最多
 * [DailyPicksAlarmScheduler.MAX_ATTEMPTS] 次后放弃当天，静默等下一天——
 * 通知宁缺勿错，不拿旧内容打扰用户。
 *
 * 自续期链：除「开关已关」和重试外，每个终局都必须重排下一天，漏排即断链。
 * 断链后**唯一**的恢复路径是 [DailyPicksAlarmScheduler.reconcile] 的冷启动对账
 * （系统广播重排已移除，理由见模块 manifest），要等用户下次打开 app 才生效——
 * 这段真空期的频率与时长由 `notification_delivery(step=relinked)` 埋点量化。
 */
class DailyPicksAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // 只认自家闹钟：组件无 intent-filter，正常情况下收不到别的 action
        if (intent.action != DailyPicksAlarmScheduler.ACTION_FIRE) return

        val appContext = context.applicationContext
        val targetAt = intent.getLongExtra(
            DailyPicksAlarmScheduler.EXTRA_TARGET_AT, System.currentTimeMillis()
        )
        val attempt = intent.getIntExtra(DailyPicksAlarmScheduler.EXTRA_ATTEMPT, 0)
        val trigger = intent.getStringExtra(DailyPicksAlarmScheduler.EXTRA_TRIGGER) ?: "unknown"
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                deliver(appContext, targetAt, attempt, trigger)
            } catch (_: Exception) {
                // 任何漏网异常都不能断链
                DailyPicksAlarmScheduler.scheduleNextDay(appContext)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun deliver(context: Context, targetAt: Long, attempt: Int, trigger: String) {
        val settings = globalSettingsManager
        // 兜底：闹钟与开关状态脱节（如取消失败）时以开关为准；不排下一天，链就此终止，
        // 重新开启时 enable 会重排
        if (!settings.currentDailyPicksNotificationEnabled()) return

        val lang = settings.currentContentLang()
        val picks = try {
            withTimeout(FETCH_TIMEOUT_MS) { TrendingRepository.shared.getPicks(lang) }
        } catch (e: Exception) {
            null
        }
        // 与 Picks 页空态判定同口径：只认两档（speedRead/controversy 已退役，UI 不再渲染）
        val items = picks?.let { it.debut + it.deepDive }.orEmpty()
        val date = picks?.metadata?.date

        if (items.isEmpty() || date == null ||
            !shouldNotify(DailyPicksPrefs.lastNotifiedDate(context), date)
        ) {
            if (attempt + 1 < DailyPicksAlarmScheduler.MAX_ATTEMPTS) {
                DailyPicksAlarmScheduler.scheduleRetry(context, targetAt, attempt + 1)
            } else {
                DailyPicksAlarmScheduler.scheduleNextDay(context)
                trackFlushed(
                    AppEvent.NotificationDelivery(
                        NotificationStep.SKIPPED,
                        NotificationKind.DAILY_PICKS,
                        reason = "gave_up",
                    )
                )
            }
            return
        }

        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            // 权限被系统侧收回：跳过当天、链保持，等用户重新授权后次日恢复。
            // 上报 skipped 让「开关开着却收不到」在埋点侧可见（此前是静默的）
            DailyPicksAlarmScheduler.scheduleNextDay(context)
            trackFlushed(
                AppEvent.NotificationDelivery(
                    NotificationStep.SKIPPED,
                    NotificationKind.DAILY_PICKS,
                    reason = "permission_revoked",
                )
            )
            return
        }

        postNotification(context, date, firstTitle = items.first().title, total = items.size, lang = lang)
        DailyPicksPrefs.setLastNotifiedDate(context, date)
        DailyPicksAlarmScheduler.scheduleNextDay(context)
        trackFlushed(
            AppEvent.NotificationDelivery(
                NotificationStep.SHOWN,
                NotificationKind.DAILY_PICKS,
                date = date,
                trigger = trigger,
                attempt = attempt,
                delayMin = ((System.currentTimeMillis() - targetAt) / 60_000).toInt(),
            )
        )
    }

    /**
     * 显式 flush 而不是留一段上传窗口：无界面进程收不到 onStop，事件否则要躺到下次启动。
     * 队列已落盘，flush 失败也不丢——这里等的是「尽早送出去」，不是「别丢」。
     */
    private suspend fun trackFlushed(event: AppEvent) {
        track(event)
        Eventbase.flush()
    }

    private fun postNotification(
        context: Context,
        date: String,
        firstTitle: String,
        total: Int,
        lang: String,
    ) {
        // 通知文案跟随 app 内容语言（与摘要/订阅同口径），而非仅系统语言：
        // API 33 以下 AppCompat 的应用内语言不作用于 receiver 的 context，需手动定位
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
                putExtra(EXTRA_NOTIFIED_DATE, date)
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
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS 在 areNotificationsEnabled 检查后被收回（竞态窗口极小）：
            // 静默放弃本次通知，符合"宁缺勿错"策略；lint 的跨方法流分析看不到上游守卫
        }
    }

    private fun localizedContext(context: Context, lang: String): Context {
        val config = Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(lang))
        }
        return context.createConfigurationContext(config)
    }

    companion object {
        // goAsync 的后台时限约 10 秒：拉取 + 上传窗口 + 开销必须收在其内
        private const val FETCH_TIMEOUT_MS = 6_000L
        private const val CHANNEL_ID = "daily_picks"
        private const val NOTIFICATION_ID = 0x9101
    }
}
