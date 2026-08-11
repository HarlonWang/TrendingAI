package whl.trending.ai.core

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class DateTimeUtilsTest {

    /** 与实现中 private dateTimeFormat 等价的格式：yyyy-MM-dd HH:mm:ss */
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

    @Test
    fun iso8601_with_z_is_parsed_and_converted() {
        // GitHub events 格式：含 T 分隔符 + Z 时区
        val input = "2026-06-09T12:47:28Z"
        val result = DateTimeUtils.formatToLocalTime(input)
        assertTrue(result.isNotEmpty())
        // 输出为 yyyy-MM-dd HH:mm:ss 本地时间，必然不含 T/Z，说明确实走了解析转换
        assertNotEquals(input, result)
        assertTrue(!result.contains('T') && !result.contains('Z'))
    }

    @Test
    fun legacy_format_is_parsed_and_converted() {
        // 内部 API 格式回归：yyyy-MM-dd HH:mm:ss（UTC）
        // 时区无关：测试内按相同逻辑自行计算期望值（UTC 解析 → 本地时区 → 同格式输出）
        val input = "2026-02-15 00:17:20"
        val expected = LocalDateTime.parse(input, dateTimeFormat)
            .toInstant(TimeZone.UTC)
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .format(dateTimeFormat)
        assertEquals(expected, DateTimeUtils.formatToLocalTime(input))
    }

    @Test
    fun empty_string_returns_empty() {
        assertEquals("", DateTimeUtils.formatToLocalTime(""))
    }

    @Test
    fun malformed_input_returns_original_string() {
        // 解析失败走 catch 分支，原样返回
        val input = "not-a-date"
        assertEquals(input, DateTimeUtils.formatToLocalTime(input))
    }

    @Test
    fun relative_time_buckets_are_classified() {
        val now = Instant.parse("2026-06-13T12:00:00Z")
        fun at(d: kotlin.time.Duration) = (now - d).toString()

        assertEquals(DateTimeUtils.RelativeUnit.JUST_NOW, DateTimeUtils.relativeTime(at(30.seconds), now).unit)

        val m = DateTimeUtils.relativeTime(at(5.minutes), now)
        assertEquals(DateTimeUtils.RelativeUnit.MINUTES, m.unit)
        assertEquals(5, m.value)

        val h = DateTimeUtils.relativeTime(at(3.hours), now)
        assertEquals(DateTimeUtils.RelativeUnit.HOURS, h.unit)
        assertEquals(3, h.value)

        val d = DateTimeUtils.relativeTime(at(2.days), now)
        assertEquals(DateTimeUtils.RelativeUnit.DAYS, d.unit)
        assertEquals(2, d.value)

        // 超过 7 天回退到绝对时间
        assertEquals(DateTimeUtils.RelativeUnit.ABSOLUTE, DateTimeUtils.relativeTime(at(10.days), now).unit)
    }

    @Test
    fun relative_time_malformed_falls_back_to_absolute() {
        val now = Instant.parse("2026-06-13T12:00:00Z")
        val result = DateTimeUtils.relativeTime("not-a-date", now)
        assertEquals(DateTimeUtils.RelativeUnit.ABSOLUTE, result.unit)
        assertEquals("not-a-date", result.absolute)
    }

    // updateStamp 的断言刻意避开 YESTERDAY：本地日期由运行环境时区决定，"now - 24h"
    // 在 DST 切换日的某些时区会仍落在同一天。同一时刻（diff=0）与 -30 天则在任何时区都成立。
    @Test
    fun update_stamp_same_instant_is_today() {
        val now = Instant.parse("2026-06-13T12:00:00Z")
        val stamp = DateTimeUtils.updateStamp(now.toString(), now)
        assertEquals(DateTimeUtils.UpdateStampUnit.TODAY, stamp.unit)
        // HH:mm，零填充到定长 5
        assertEquals(5, stamp.time.length)
        assertEquals(':', stamp.time[2])
    }

    @Test
    fun update_stamp_long_past_is_earlier_with_date_parts() {
        val now = Instant.parse("2026-06-13T12:00:00Z")
        val stamp = DateTimeUtils.updateStamp((now - 30.days).toString(), now)
        assertEquals(DateTimeUtils.UpdateStampUnit.EARLIER, stamp.unit)
        assertTrue(stamp.month in 1..12)
        assertTrue(stamp.day in 1..31)
    }

    @Test
    fun update_stamp_future_instant_degrades_to_today() {
        // 设备时钟慢于服务端时，抓取时刻会落在"未来"——宁可说今天，也不要冒出负数天
        val now = Instant.parse("2026-06-13T12:00:00Z")
        val stamp = DateTimeUtils.updateStamp((now + 2.days).toString(), now)
        assertEquals(DateTimeUtils.UpdateStampUnit.TODAY, stamp.unit)
    }

    @Test
    fun update_stamp_unparsable_is_unknown() {
        val now = Instant.parse("2026-06-13T12:00:00Z")
        assertEquals(DateTimeUtils.UpdateStampUnit.UNKNOWN, DateTimeUtils.updateStamp("", now).unit)
        assertEquals(DateTimeUtils.UpdateStampUnit.UNKNOWN, DateTimeUtils.updateStamp("not-a-date", now).unit)
    }
}
