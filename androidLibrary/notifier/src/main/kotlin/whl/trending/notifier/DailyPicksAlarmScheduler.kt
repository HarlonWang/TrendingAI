package whl.trending.notifier

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.edit
import java.util.Calendar

/**
 * 每日 Picks 通知的闹钟调度器。触发时机走 AlarmManager 而非 WorkManager（JobScheduler）：
 * 延迟作业的执行受待机分桶配额支配，激进 ROM 把 app 压进 RARE 桶后"到点"常被推迟到
 * 充电空闲窗、甚至用户打开 app 才放行——7/8 月埋点实测收到过通知的 55 台设备里仅 1 台
 * 基本准点。闹钟不走作业配额，是"用户可感知定时事件"的正确通道；准点性对比见
 * shown 埋点的 delay_min 属性。
 *
 * 分档：有精确闹钟权限（Android 11- 恒有；12/13 安装即默认授予；14+ 新装默认拒绝，
 * 设置页提供跳系统页的入口）用 setExactAndAllowWhileIdle 准点触发；无权限降级
 * setAndAllowWhileIdle。降级不选 setWindow：一夜未动的设备 9:30 常还在 Doze 里，
 * setWindow 要等维护窗口，前者 Doze 中也放行，最差情况更好。
 *
 * 闹钟不像 WorkManager 会持久化：重启/改时间/换时区由 [DailyPicksAlarmReceiver]
 * 收系统广播重排，冷启动走 [reconcile] 对账。
 */
object DailyPicksAlarmScheduler {

    internal const val ACTION_FIRE = "whl.trending.notifier.action.DAILY_PICKS_FIRE"

    /** 当天首发的计划触发时刻（epoch millis）；重试沿用首发值，shown 的 delay_min 据此计算 */
    internal const val EXTRA_TARGET_AT = "target_at"

    /** 第几次尝试，0 为当天首发 */
    internal const val EXTRA_ATTEMPT = "attempt"

    /** 排定此闹钟时用的档位：exact / inexact，透传进 shown 埋点 */
    internal const val EXTRA_TRIGGER = "trigger"

    internal const val MAX_ATTEMPTS = 5
    internal const val RETRY_INTERVAL_MS = 30L * 60 * 1000

    /**
     * 对账宽限：排期时刻已过但在此宽限内不算断链——不精确档本就可能晚点，
     * 此刻抢着重排反而把今天瞄到明天、白丢一次。
     */
    private const val RECONCILE_SLACK_MS = 6L * 60 * 60 * 1000

    // 本地 09:30：服务端 Picks 于 UTC 01:00（北京 09:00）开始生成、Newsletter 排在
    // UTC 01:15 发送。取 09:30 让 UTC+8 主力用户一次命中新内容，避免每天固定吃一轮
    // 重试；其他时区靠重试梯子兜底
    private const val NOTIFY_HOUR = 9
    private const val NOTIFY_MINUTE = 30

    /** 排下一个本地 9:30 的首发闹钟（今天未到点则今天），覆盖既有排期 */
    fun scheduleNextDay(context: Context) {
        val now = Calendar.getInstance()
        val targetAt = now.timeInMillis + initialDelayMillis(now, NOTIFY_HOUR, NOTIFY_MINUTE)
        set(context, triggerAt = targetAt, targetAt = targetAt, attempt = 0)
    }

    /** 拉取失败/内容未更新的重试：+30 分钟线性梯子，沿用当天首发的 targetAt */
    internal fun scheduleRetry(context: Context, targetAt: Long, attempt: Int) {
        set(
            context,
            triggerAt = System.currentTimeMillis() + RETRY_INTERVAL_MS,
            targetAt = targetAt,
            attempt = attempt,
        )
    }

    fun cancel(context: Context) {
        pendingIntent(context, PendingIntent.FLAG_NO_CREATE)?.let {
            alarmManager(context).cancel(it)
            it.cancel()
        }
        DailyPicksPrefs.clearNextTriggerAt(context)
    }

    /**
     * 冷启动对账：链断了才补排，不打断进行中的排期或重试梯子。断链判据取并集——
     * PendingIntent 不存在（force-stop / 重启会连闹钟一起清掉），或记录的触发时刻
     * 已过期超出宽限（进程死在终局重排之前）。PI 存在不代表闹钟还挂着（响过之后
     * PI 仍在注册表里），所以两个信号缺一不可。
     */
    fun reconcile(context: Context) {
        val piMissing = pendingIntent(context, PendingIntent.FLAG_NO_CREATE) == null
        val nextAt = DailyPicksPrefs.nextTriggerAt(context)
        val stale = nextAt == 0L || nextAt < System.currentTimeMillis() - RECONCILE_SLACK_MS
        if (piMissing || stale) {
            scheduleNextDay(context)
        }
    }

    /**
     * 同槽重排：保持触发时刻与重试进度不变，只按当前权限重挑档位。
     * 用于精确闹钟权限变更广播——直接 scheduleNextDay 会把进行中的当日首发或
     * 重试梯子覆盖到明天（Sourcery 审查指出）；换成 reconcile 又会在排期健康时
     * 什么都不做、升档永不生效。排期缺失或已过期时退回重排下一天。
     */
    fun rescheduleSameSlot(context: Context) {
        val triggerAt = DailyPicksPrefs.nextTriggerAt(context)
        if (triggerAt > System.currentTimeMillis()) {
            set(
                context,
                triggerAt = triggerAt,
                targetAt = DailyPicksPrefs.nextTargetAt(context).takeIf { it != 0L } ?: triggerAt,
                attempt = DailyPicksPrefs.nextAttempt(context),
            )
        } else {
            scheduleNextDay(context)
        }
    }

    /** 精确闹钟当前是否可用（分档依据；设置页「准点提醒」入口按它决定显隐） */
    fun canExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager(context).canScheduleExactAlarms()

    private fun set(context: Context, triggerAt: Long, targetAt: Long, attempt: Int) {
        val am = alarmManager(context)
        val exact = canExact(context)
        val pi = pendingIntent(
            context,
            PendingIntent.FLAG_UPDATE_CURRENT,
            targetAt = targetAt,
            attempt = attempt,
            trigger = if (exact) "exact" else "inexact",
        )!!
        if (exact) {
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                DailyPicksPrefs.setSchedule(context, triggerAt, targetAt, attempt)
                return
            } catch (_: SecurityException) {
                // canExact 与 set 之间权限恰被收回的竞态：落到不精确档
            }
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        DailyPicksPrefs.setSchedule(context, triggerAt, targetAt, attempt)
    }

    private fun pendingIntent(
        context: Context,
        flags: Int,
        targetAt: Long = 0,
        attempt: Int = 0,
        trigger: String = "",
    ): PendingIntent? {
        // extras 不参与 PendingIntent 匹配：FLAG_NO_CREATE 探测与 cancel 用默认参数
        // 也能命中同一个 PI；FLAG_UPDATE_CURRENT 则把新 extras 顶进去
        val intent = Intent(context, DailyPicksAlarmReceiver::class.java)
            .setAction(ACTION_FIRE)
            .putExtra(EXTRA_TARGET_AT, targetAt)
            .putExtra(EXTRA_ATTEMPT, attempt)
            .putExtra(EXTRA_TRIGGER, trigger)
        return PendingIntent.getBroadcast(
            context, 0, intent, flags or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun alarmManager(context: Context): AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
}

/**
 * 通知链路的本地状态，沿用 Worker 时代的 prefs 文件（last_notified_date 记录跨迁移延续）。
 */
internal object DailyPicksPrefs {
    private const val NAME = "daily_picks_notifier"
    private const val KEY_LAST_NOTIFIED_DATE = "last_notified_date"
    private const val KEY_NEXT_TRIGGER_AT = "next_trigger_at"
    private const val KEY_NEXT_TARGET_AT = "next_target_at"
    private const val KEY_NEXT_ATTEMPT = "next_attempt"
    private const val KEY_LAST_OPENED_DATE = "last_opened_date"

    private fun prefs(context: Context) =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun lastNotifiedDate(context: Context): String? =
        prefs(context).getString(KEY_LAST_NOTIFIED_DATE, null)

    fun setLastNotifiedDate(context: Context, date: String) {
        prefs(context).edit { putString(KEY_LAST_NOTIFIED_DATE, date) }
    }

    fun nextTriggerAt(context: Context): Long =
        prefs(context).getLong(KEY_NEXT_TRIGGER_AT, 0L)

    fun nextTargetAt(context: Context): Long =
        prefs(context).getLong(KEY_NEXT_TARGET_AT, 0L)

    fun nextAttempt(context: Context): Int =
        prefs(context).getInt(KEY_NEXT_ATTEMPT, 0)

    /** 记录在排闹钟的完整载荷，供对账与同槽重排（换档不换排期）使用 */
    fun setSchedule(context: Context, triggerAt: Long, targetAt: Long, attempt: Int) {
        prefs(context).edit {
            putLong(KEY_NEXT_TRIGGER_AT, triggerAt)
            putLong(KEY_NEXT_TARGET_AT, targetAt)
            putInt(KEY_NEXT_ATTEMPT, attempt)
        }
    }

    fun clearNextTriggerAt(context: Context) {
        prefs(context).edit {
            remove(KEY_NEXT_TRIGGER_AT)
            remove(KEY_NEXT_TARGET_AT)
            remove(KEY_NEXT_ATTEMPT)
        }
    }

    /**
     * open 埋点去重：同一天的通知只上报一次（最近任务重建会重投原始 intent，
     * `removeExtra` 只挡得住配置变更；8 月数据里 open 因此有重放污染）。
     * 无 date 的旧版通知（升级期一次性存量）按旧行为放行。
     */
    fun consumeOpen(context: Context, date: String?): Boolean {
        if (date.isNullOrBlank()) return true
        val p = prefs(context)
        if (p.getString(KEY_LAST_OPENED_DATE, null) == date) return false
        p.edit { putString(KEY_LAST_OPENED_DATE, date) }
        return true
    }
}
