package whl.trending.notifier

import java.time.Duration
import java.time.ZonedDateTime

/**
 * 距下一个本地 [targetHour] 整点的毫秒数，作为周期任务的 initialDelay。
 * 恰在整点时取次日，避免 0 延迟当场触发。
 */
fun initialDelayMillis(now: ZonedDateTime, targetHour: Int): Long {
    var next = now.toLocalDate().atTime(targetHour, 0).atZone(now.zone)
    if (!next.isAfter(now)) {
        next = next.plusDays(1)
    }
    return Duration.between(now, next).toMillis()
}

/**
 * 同一天内容只通知一次：picks 的 metadata.date 与上次已通知日期相同（或为空）时跳过。
 */
fun shouldNotify(lastNotifiedDate: String?, newDate: String): Boolean =
    newDate.isNotBlank() && newDate != lastNotifiedDate
