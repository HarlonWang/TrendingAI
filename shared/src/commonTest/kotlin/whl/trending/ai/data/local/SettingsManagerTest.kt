package whl.trending.ai.data.local

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsManagerTest {

    private lateinit var manager: SettingsManager

    @BeforeTest
    fun setUp() {
        manager = SettingsManager(MapSettings())
    }

    @Test
    fun seedColor_default_is_baseline_purple() = runTest {
        val first = manager.seedColor.first()
        assertEquals(0xFF6750A4L, first)
    }

    @Test
    fun setSeedColor_updates_flow() = runTest {
        manager.setSeedColor(0xFF1976D2L)
        assertEquals(0xFF1976D2L, manager.seedColor.first())
    }

    @Test
    fun seedColor_and_themeMode_are_independent() = runTest {
        manager.setSeedColor(0xFFC2185BL)
        manager.setThemeMode(ThemeMode.DARK)

        assertEquals(0xFFC2185BL, manager.seedColor.first())
        assertEquals(ThemeMode.DARK, manager.themeMode.first())

        manager.setThemeMode(ThemeMode.LIGHT)
        assertEquals(0xFFC2185BL, manager.seedColor.first())
        assertEquals(ThemeMode.LIGHT, manager.themeMode.first())
    }
}
