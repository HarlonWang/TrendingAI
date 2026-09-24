package whl.trending.ai.tinyui

import app.tinyui.updates.Source
import app.tinyui.updates.UpdateEvent
import com.russhwolf.settings.MapSettings
import whl.trending.ai.core.analytics.AppEvent
import whl.trending.ai.data.local.SettingsManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class TinyUIRunningReportTest {

    private val settings = SettingsManager(MapSettings())
    private val tracked = mutableListOf<AppEvent>()

    private fun start(version: String, at: String, source: Source = Source.INSTALLED, channel: String = "production") =
        reportRunningOnce(UpdateEvent.Running("trendingai", version, source), channel, Instant.parse(at), settings) { tracked += it }

    @Test
    fun reportsOncePerDayForTheSameVersion() {
        start("v1", "2026-09-25T01:00:00Z")
        start("v1", "2026-09-25T09:00:00Z")
        start("v1", "2026-09-25T15:59:59Z")
        assertEquals(listOf<AppEvent>(AppEvent.TinyUIRunning("trendingai", "v1", Source.INSTALLED, "production", HOST_VERSION)), tracked)
    }

    @Test
    fun theDayTurnsAtMidnightUtcPlus8() {
        start("v1", "2026-09-25T15:59:59Z")
        start("v1", "2026-09-25T16:00:00Z")
        assertEquals(2, tracked.size, "16:00Z is 00:00 the next day in UTC+8, eventbase's day boundary")
    }

    @Test
    fun aNewVersionSourceOrChannelTheSameDayReportsAgain() {
        start("v0", "2026-09-25T01:00:00Z", source = Source.EMBEDDED)
        start("v1", "2026-09-25T02:00:00Z")
        start("v1", "2026-09-25T03:00:00Z", channel = "staging")
        start("v1", "2026-09-25T04:00:00Z", channel = "staging")
        assertEquals(
            listOf("v0/EMBEDDED/production", "v1/INSTALLED/production", "v1/INSTALLED/staging"),
            tracked.map { (it as AppEvent.TinyUIRunning).let { e -> "${e.version}/${e.source}/${e.updateChannel}" } },
        )
    }
}
