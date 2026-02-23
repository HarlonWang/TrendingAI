package whl.trending.ai.core

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.number
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

object DateTimeUtils {
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
     * API 格式: "2026-02-15 00:17:20"
     */
    fun formatToLocalTime(utcString: String): String {
        if (utcString.isEmpty()) return ""
        return try {
            // 1. 解析为无时区的 LocalDateTime
            val utcLocalDateTime = LocalDateTime.parse(utcString, dateTimeFormat)
            
            // 2. 转换为 UTC 时区的 Instant
            val instant = utcLocalDateTime.toInstant(TimeZone.UTC)
            
            // 3. 转换为本地时区的 LocalDateTime
            val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
            
            // 4. 格式化输出
            localDateTime.format(dateTimeFormat)
        } catch (e: Exception) {
            utcString
        }
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

    /**
     * 获取 UTC 时分对应的本地时间字符串 (HH:mm)
     */
    fun getLocalTimeFromUtc(hour: Int, minute: Int): String {
        val now = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        val utcDateTime = LocalDateTime(now.year, now.month, now.day, hour, minute)
        val localDateTime = utcDateTime.toInstant(TimeZone.UTC).toLocalDateTime(TimeZone.currentSystemDefault())
        return "${localDateTime.hour.toString().padStart(2, '0')}:${localDateTime.minute.toString().padStart(2, '0')}"
    }
}
