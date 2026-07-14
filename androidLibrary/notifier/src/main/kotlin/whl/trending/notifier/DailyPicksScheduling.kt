package whl.trending.notifier

import java.util.Calendar

/**
 * 距下一个本地 [targetHour]:[targetMinute] 的毫秒数，作为周期任务的 initialDelay。
 * 恰在该时刻时取次日，避免 0 延迟当场触发。
 *
 * 用 java.util.Calendar 而非 java.time：minSdk 24 上 java.time 需 API 26 或 desugaring
 * （Android 7.x 会 NoSuchMethodError），本地墙钟语义 Calendar 等价且全版本可用。
 */
fun initialDelayMillis(now: Calendar, targetHour: Int, targetMinute: Int = 0): Long {
    val next = (now.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, targetHour)
        set(Calendar.MINUTE, targetMinute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (timeInMillis <= now.timeInMillis) {
            add(Calendar.DAY_OF_MONTH, 1)
        }
    }
    return next.timeInMillis - now.timeInMillis
}

/**
 * 同一天内容只通知一次：picks 的 metadata.date 与上次已通知日期相同（或为空）时跳过。
 */
fun shouldNotify(lastNotifiedDate: String?, newDate: String): Boolean =
    newDate.isNotBlank() && newDate != lastNotifiedDate
