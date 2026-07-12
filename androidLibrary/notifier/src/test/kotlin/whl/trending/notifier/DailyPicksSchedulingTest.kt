package whl.trending.notifier

import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DailyPicksSchedulingTest {

    private fun at(hour: Int, minute: Int): ZonedDateTime =
        ZonedDateTime.of(2026, 7, 11, hour, minute, 0, 0, ZoneId.of("Asia/Shanghai"))

    @Test
    fun beforeTargetHourDelaysUntilSameDay() {
        // 07:30 → 当天 09:00，差 1.5h
        assertEquals(90 * 60 * 1000L, initialDelayMillis(at(7, 30), targetHour = 9))
    }

    @Test
    fun afterTargetHourDelaysUntilNextDay() {
        // 10:00 → 次日 09:00，差 23h
        assertEquals(23 * 60 * 60 * 1000L, initialDelayMillis(at(10, 0), targetHour = 9))
    }

    @Test
    fun exactlyAtTargetHourDelaysUntilNextDay() {
        // 恰在 09:00 → 次日 09:00（避免 0 延迟立即触发）
        assertEquals(24 * 60 * 60 * 1000L, initialDelayMillis(at(9, 0), targetHour = 9))
    }

    @Test
    fun supportsTargetMinute() {
        // 09:00 → 当天 09:30，差 30 分钟
        assertEquals(30 * 60 * 1000L, initialDelayMillis(at(9, 0), targetHour = 9, targetMinute = 30))
        // 09:45 → 次日 09:30
        assertEquals((24 * 60 - 15) * 60 * 1000L, initialDelayMillis(at(9, 45), targetHour = 9, targetMinute = 30))
    }

    @Test
    fun notifiesWhenDateIsNew() {
        assertTrue(shouldNotify(lastNotifiedDate = "2026-07-10", newDate = "2026-07-11"))
    }

    @Test
    fun notifiesOnFirstRunWithoutHistory() {
        assertTrue(shouldNotify(lastNotifiedDate = null, newDate = "2026-07-11"))
    }

    @Test
    fun skipsWhenDateAlreadyNotified() {
        assertFalse(shouldNotify(lastNotifiedDate = "2026-07-11", newDate = "2026-07-11"))
    }

    @Test
    fun skipsWhenDateIsBlank() {
        assertFalse(shouldNotify(lastNotifiedDate = null, newDate = ""))
    }
}
