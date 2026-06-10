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
}
