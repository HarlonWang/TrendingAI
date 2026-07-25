package whl.trending.ai.data.local

import com.russhwolf.settings.MapSettings
import whl.trending.ai.ui.theme.PRESET_PALETTE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 主题色从 10 档收拢到 6 档后的升级兼容：选过被删档位的老用户不能出现
 * 「App 是橙色的，设置页里一个圆都没选中」这种状态。
 */
class ThemeSeedMigrationTest {

    private val presetArgbs = PRESET_PALETTE.map { it.argb }.toSet()

    /** 旧版本里已被删掉的 5 个预设色 */
    private val removedLegacySeeds = listOf(
        0xFFF4511EL, // orange
        0xFF00897BL, // teal
        0xFF0288D1L, // cyan
        0xFF3F51B5L, // indigo
        0xFFC2185BL, // pink
    )

    private fun managerWith(vararg entries: Pair<String, Any>): Pair<SettingsManager, MapSettings> {
        val settings = MapSettings()
        entries.forEach { (key, value) ->
            when (value) {
                is Long -> settings.putLong(key, value)
                is Boolean -> settings.putBoolean(key, value)
                is String -> settings.putString(key, value)
                else -> error("unsupported test value $value")
            }
        }
        return SettingsManager(settings) to settings
    }

    @Test
    fun removed_preset_seed_becomes_custom_theme_with_same_color() {
        removedLegacySeeds.forEach { legacy ->
            val (manager, _) = managerWith("prefs_seed_color" to legacy)

            manager.migrateLegacySeedIfNeeded(presetArgbs)

            assertTrue(manager.currentThemeCustom(), "被删档位 ${legacy.toString(16)} 应迁到自定义档")
            assertEquals(legacy, manager.currentCustomSeedColor())
            // 关键：生效色一个像素都不变，用户升级后看不出差别
            assertEquals(legacy, manager.currentSeedColor())
            // 旧版本所有预设都是 TonalSpot + 标准对比度，照搬保持观感
            assertEquals(DEFAULT_THEME_STYLE_STORAGE, manager.currentThemeStyle())
            assertEquals(DEFAULT_THEME_CONTRAST_STORAGE, manager.currentThemeContrast())
        }
    }

    @Test
    fun surviving_preset_seed_stays_a_preset() {
        PRESET_PALETTE.forEach { seed ->
            val (manager, _) = managerWith("prefs_seed_color" to seed.argb)

            manager.migrateLegacySeedIfNeeded(presetArgbs)

            assertFalse(manager.currentThemeCustom(), "预设 ${seed.id} 不该被迁成自定义档")
            assertNull(manager.currentCustomSeedColor())
            assertEquals(seed.argb, manager.currentSeedColor())
        }
    }

    @Test
    fun fresh_install_writes_nothing() {
        val (manager, settings) = managerWith()

        manager.migrateLegacySeedIfNeeded(presetArgbs)

        assertFalse(settings.hasKey("prefs_theme_custom"))
        assertFalse(settings.hasKey("prefs_theme_custom_seed"))
        assertFalse(manager.currentThemeCustom())
        assertEquals(DEFAULT_SEED_ARGB, manager.currentSeedColor())
    }

    @Test
    fun migration_does_not_clobber_an_existing_custom_theme() {
        // 已在新版本调过色的用户：迁移必须完全跳过，否则会把他调好的风格/对比度冲掉
        val (manager, _) = managerWith(
            "prefs_seed_color" to 0xFFD219ABL,
            "prefs_theme_custom" to true,
            "prefs_theme_custom_seed" to 0xFFD219ABL,
            "prefs_theme_style" to "vivid",
            "prefs_theme_contrast" to "high",
        )

        manager.migrateLegacySeedIfNeeded(presetArgbs)

        assertTrue(manager.currentThemeCustom())
        assertEquals(0xFFD219ABL, manager.currentCustomSeedColor())
        assertEquals("vivid", manager.currentThemeStyle())
        assertEquals("high", manager.currentThemeContrast())
    }

    @Test
    fun migration_is_idempotent_across_launches() {
        val (manager, _) = managerWith("prefs_seed_color" to 0xFFC2185BL)

        manager.migrateLegacySeedIfNeeded(presetArgbs)
        // 用户升级后又主动选了预设，下次启动再跑迁移不能把他拽回自定义档
        manager.setSeedColor(PRESET_PALETTE.first().argb)
        manager.migrateLegacySeedIfNeeded(presetArgbs)

        assertFalse(manager.currentThemeCustom())
        assertEquals(PRESET_PALETTE.first().argb, manager.currentSeedColor())
        // 自定义色仍留着，用户点回第 7 颗圆就能找回原来的粉色
        assertEquals(0xFFC2185BL, manager.currentCustomSeedColor())
    }
}
