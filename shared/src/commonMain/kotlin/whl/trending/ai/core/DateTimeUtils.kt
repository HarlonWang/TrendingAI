package whl.trending.ai.core

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.number
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

object DateTimeUtils {
    /** 相对时间分桶；ABSOLUTE 表示超出相对范围（>7天）或解析失败，回退到 [absolute] 绝对时间串。 */
    enum class RelativeUnit { JUST_NOW, MINUTES, HOURS, DAYS, ABSOLUTE }
    data class RelativeTime(val unit: RelativeUnit, val value: Int = 0, val absolute: String = "")

    /** 相对时间（基于系统当前时刻）。展示侧据 [RelativeTime.unit] 选用本地化字符串。 */
    fun relativeTime(utcString: String): RelativeTime = relativeTime(utcString, Clock.System.now())

    /** 可注入 [now] 的相对时间计算，便于测试。 */
    fun relativeTime(utcString: String, now: Instant): RelativeTime {
        val instant = parseInstantOrNull(utcString)
            ?: return RelativeTime(RelativeUnit.ABSOLUTE, absolute = formatToLocalTime(utcString))
        val diff = now - instant
        return when {
            diff < 1.minutes -> RelativeTime(RelativeUnit.JUST_NOW)
            diff < 1.hours -> RelativeTime(RelativeUnit.MINUTES, diff.inWholeMinutes.toInt())
            diff < 24.hours -> RelativeTime(RelativeUnit.HOURS, diff.inWholeHours.toInt())
            diff < 7.days -> RelativeTime(RelativeUnit.DAYS, diff.inWholeDays.toInt())
            else -> RelativeTime(RelativeUnit.ABSOLUTE, absolute = formatToLocalTime(utcString))
        }
    }

    /**
     * 抓取时刻的日期锚定分桶。
     *
     * 刻意不复用 [relativeTime]：三源都是**日更节律**（HN/PH 每天一批、GitHub 每天两批，
     * 间隔 11~13 小时），相对时间会把正常节律说成「13 小时前更新」，读起来像陈旧数据；
     * 「今天 08:30 更新」传达的才是「这是今天的数据」。相对时间擅长的是连续流，不是日更。
     *
     * [UNKNOWN] 覆盖解析失败与空串，展示侧据此整行不渲染。
     */
    enum class UpdateStampUnit { TODAY, YESTERDAY, EARLIER, UNKNOWN }

    /** [time] 为本地时区的 HH:mm；[month]/[day] 仅 [UpdateStampUnit.EARLIER] 有意义。 */
    data class UpdateStamp(
        val unit: UpdateStampUnit,
        val time: String = "",
        val month: Int = 0,
        val day: Int = 0,
    )

    /**
     * 把 UTC 抓取时刻换算成本地时区的日期锚定描述。
     *
     * 未来时刻（设备时钟慢、或时钟漂移）一律归入 [UpdateStampUnit.TODAY]——宁可说「今天」，
     * 也不要冒出「-1 天前」这种自证 bug 的文案。
     */
    fun updateStamp(utcString: String, now: Instant = Clock.System.now()): UpdateStamp {
        val instant = parseInstantOrNull(utcString) ?: return UpdateStamp(UpdateStampUnit.UNKNOWN)
        val tz = TimeZone.currentSystemDefault()
        val local = instant.toLocalDateTime(tz)
        val today = now.toLocalDateTime(tz).date
        val time = "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
        val diffDays = today.toEpochDays() - local.date.toEpochDays()
        val unit = when {
            diffDays <= 0L -> UpdateStampUnit.TODAY
            diffDays == 1L -> UpdateStampUnit.YESTERDAY
            else -> UpdateStampUnit.EARLIER
        }
        return UpdateStamp(unit, time, local.month.number, local.day)
    }

    /**
     * 距未来时刻的剩余整小时数（向上取整；已过期返回 0）。解析失败返回 null。
     * 用于配额卡「约 N 小时后重置」——分钟级精度对每日重置没有意义，不做倒计时刷新。
     */
    fun hoursUntil(utcString: String, now: Instant = Clock.System.now()): Int? {
        val instant = parseInstantOrNull(utcString) ?: return null
        val diff = instant - now
        if (diff.isNegative()) return 0
        val whole = diff.inWholeHours
        return if (diff - whole.hours > kotlin.time.Duration.ZERO) (whole + 1).toInt() else whole.toInt()
    }

    private fun parseInstantOrNull(utcString: String): Instant? {
        if (utcString.isEmpty()) return null
        return try {
            if (utcString.contains('T')) {
                Instant.parse(utcString)
            } else {
                LocalDateTime.parse(utcString, dateTimeFormat).toInstant(TimeZone.UTC)
            }
        } catch (e: Exception) {
            null
        }
    }
    // 定义 yyyy-MM-dd HH:mm:ss 格式
    private val dateTimeFormat = LocalDateTime.Format {
        year()
        char('-')
        monthNumber()
        char('-')
        day()
        char(' ')
        hour()
        char(':')
        minute()
        char(':')
        second()
    }

    /**
     * 将 API 返回的 UTC 时间字符串转换为本地时区对应的日期时间字符串。
     * 支持两种格式：
     * - "2026-02-15 00:17:20"（内部 API 格式）
     * - "2026-06-09T12:47:28Z"（ISO 8601，GitHub events）
     */
    fun formatToLocalTime(utcString: String): String {
        if (utcString.isEmpty()) return ""
        return try {
            // 优先尝试 ISO 8601（含 T 分隔符）
            val instant = if (utcString.contains('T')) {
                kotlin.time.Instant.parse(utcString)
            } else {
                // 内部 API 格式：yyyy-MM-dd HH:mm:ss
                val utcLocalDateTime = LocalDateTime.parse(utcString, dateTimeFormat)
                utcLocalDateTime.toInstant(TimeZone.UTC)
            }
            val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
            localDateTime.format(dateTimeFormat)
        } catch (e: Exception) {
            utcString
        }
    }

    /**
     * 将整数格式化为带千位分隔符的字符串，例如 20846 → "20,846"。
     */
    fun formatNumber(n: Int): String {
        val s = n.toString()
        val sb = StringBuilder()
        val start = s.length % 3
        if (start > 0) sb.append(s.substring(0, start))
        var i = start
        while (i < s.length) {
            if (sb.isNotEmpty()) sb.append(',')
            sb.append(s.substring(i, i + 3))
            i += 3
        }
        return sb.toString()
    }

    /**
     * 将 DatePicker 返回的毫秒值转换为 YYYY-MM-DD 字符串。
     */
    fun formatEpochMillisToDate(millis: Long?): String {
        if (millis == null) return ""
        val instant = Instant.fromEpochMilliseconds(millis)
        val date = instant.toLocalDateTime(TimeZone.UTC).date
        return "${date.year}-${date.month.number.toString().padStart(2, '0')}-${date.day.toString().padStart(2, '0')}"
    }
}
