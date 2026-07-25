package whl.trending.ai.data.local

import com.russhwolf.settings.MapSettings
import whl.trending.ai.ui.theme.PRESET_PALETTE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 自定义色历史：调色是试错过程，只留一个「当前自定义色」的话，调错一次或点一下
 * 恢复默认，之前调好的就永久没了。这些用例守住「试错不丢失」这条底线。
 */
class CustomThemeHistoryTest {

    private fun manager(vararg entries: Pair<String, Any>): SettingsManager {
        val settings = MapSettings()
        entries.forEach { (k, v) ->
            when (v) {
                is Long -> settings.putLong(k, v)
                is Boolean -> settings.putBoolean(k, v)
                is String -> settings.putString(k, v)
                else -> error("unsupported $v")
            }
        }
        return SettingsManager(settings)
    }

    @Test
    fun history_keeps_newest_first() {
        val m = manager()
        m.pushCustomThemeHistory(CustomThemeEntry(0xFF112233L))
        m.pushCustomThemeHistory(CustomThemeEntry(0xFF445566L))

        assertEquals(
            listOf(0xFF445566L, 0xFF112233L),
            m.currentCustomThemeHistory().map { it.seedArgb },
        )
    }

    @Test
    fun identical_entry_is_promoted_not_duplicated() {
        val m = manager()
        val a = CustomThemeEntry(0xFF112233L)
        val b = CustomThemeEntry(0xFF445566L)
        m.pushCustomThemeHistory(a)
        m.pushCustomThemeHistory(b)
        m.pushCustomThemeHistory(a)

        assertEquals(listOf(a, b), m.currentCustomThemeHistory())
    }

    @Test
    fun same_color_with_different_style_is_a_separate_entry() {
        // 同色不同风格是两套观感，不该互相顶掉
        val m = manager()
        val soft = CustomThemeEntry(0xFF112233L, style = "soft")
        val vivid = CustomThemeEntry(0xFF112233L, style = "vivid")
        m.pushCustomThemeHistory(soft)
        m.pushCustomThemeHistory(vivid)

        assertEquals(listOf(vivid, soft), m.currentCustomThemeHistory())
    }

    @Test
    fun history_is_capped() {
        val m = manager()
        repeat(12) { m.pushCustomThemeHistory(CustomThemeEntry(0xFF000000L + it)) }

        val history = m.currentCustomThemeHistory()
        assertEquals(8, history.size)
        // 留下的是最近 8 条，最新在前
        assertEquals(0xFF00000BL, history.first().seedArgb)
        assertEquals(0xFF000004L, history.last().seedArgb)
    }

    @Test
    fun reset_to_default_clears_custom_slot_but_keeps_history() {
        // 「恢复默认」= 回到没调过色的样子：自定义档被清掉，但历史留着，
        // 用户随时能从最近使用里整组取回——这是这个操作能安全存在的前提
        val m = manager()
        m.setCustomTheme(0xFFD219ABL, "vivid", "high")
        m.pushCustomThemeHistory(CustomThemeEntry(0xFFD219ABL, "vivid", "high"))

        m.clearCustomTheme()

        assertEquals(DEFAULT_SEED_ARGB, m.currentSeedColor())
        assertTrue(!m.currentThemeCustom())
        // 自定义档本身清掉：色板末尾那颗圆变回色轮加号
        assertNull(m.currentCustomSeedColor())
        assertEquals(DEFAULT_THEME_STYLE_STORAGE, m.currentThemeStyle())
        // 历史完好，是唯一的找回路径
        assertEquals(
            listOf(CustomThemeEntry(0xFFD219ABL, "vivid", "high")),
            m.currentCustomThemeHistory(),
        )
    }

    @Test
    fun reset_from_a_preset_keeps_that_preset() {
        // 用户从某个预设档进调色台、什么都没改就点恢复默认：
        // 该清掉的是自定义档，不该顺手把他选的预设也改成默认紫
        val preset = PRESET_PALETTE.last { it.argb != DEFAULT_SEED_ARGB }
        val m = manager("prefs_seed_color" to preset.argb, "prefs_theme_custom" to false)

        m.clearCustomTheme()

        assertEquals(preset.argb, m.currentSeedColor())
        assertTrue(!m.currentThemeCustom())
    }

    @Test
    fun migration_sentinel_survives_everyday_writes() {
        // 哨兵必须独立于业务 key：早期版本借用 prefs_theme_custom 当标记，
        // 而 setSeedColor 也会写它——一次发版后哨兵就被点亮，
        // 下次再精简色板时迁移会被整体跳过，守不住被删的档位
        val settings = MapSettings()
        settings.putLong("prefs_seed_color", 0xFF00BCD4L)
        val m = SettingsManager(settings)

        // 日常操作：选预设、清自定义，都会写 prefs_theme_custom
        m.setSeedColor(PRESET_PALETTE.first().argb)
        m.clearCustomTheme()

        // 迁移仍未跑过，此时应照常生效
        settings.putLong("prefs_seed_color", 0xFF00BCD4L)
        m.migrateLegacySeedIfNeeded(PRESET_PALETTE.map { it.argb }.toSet())

        assertTrue(m.currentThemeCustom(), "哨兵被业务 key 写脏，迁移被误判为已跑过")
        assertEquals(0xFF00BCD4L, m.currentCustomSeedColor())
    }

    @Test
    fun migration_runs_once_per_version() {
        val legacy = 0xFF00BCD4L
        val m = manager("prefs_seed_color" to legacy)
        val presets = PRESET_PALETTE.map { it.argb }.toSet()

        m.migrateLegacySeedIfNeeded(presets)
        // 用户随后选了预设，再次启动跑迁移不该把他拽回自定义档
        m.setSeedColor(PRESET_PALETTE.first().argb)
        m.migrateLegacySeedIfNeeded(presets)

        assertTrue(!m.currentThemeCustom())
        assertEquals(PRESET_PALETTE.first().argb, m.currentSeedColor())
    }

    @Test
    fun migrated_legacy_color_lands_in_history() {
        // 老用户不知道「自定义」那档装的是他原来的颜色，必须进历史兜底
        val legacy = 0xFF00BCD4L
        val m = manager("prefs_seed_color" to legacy)

        m.migrateLegacySeedIfNeeded(PRESET_PALETTE.map { it.argb }.toSet())

        assertEquals(listOf(legacy), m.currentCustomThemeHistory().map { it.seedArgb })
        assertEquals(DEFAULT_THEME_STYLE_STORAGE, m.currentCustomThemeHistory().first().style)
    }

    @Test
    fun migrated_color_survives_reset_to_default() {
        // 完整链路：迁移进来 → 用户点恢复默认 → 原色仍能从历史取回
        val legacy = 0xFF00BCD4L
        val m = manager("prefs_seed_color" to legacy)
        m.migrateLegacySeedIfNeeded(PRESET_PALETTE.map { it.argb }.toSet())

        m.clearCustomTheme()

        assertEquals(listOf(legacy), m.currentCustomThemeHistory().map { it.seedArgb })
    }

    @Test
    fun corrupted_history_falls_back_to_empty() {
        val m = manager("prefs_theme_custom_history" to "{not json")
        assertEquals(emptyList(), m.currentCustomThemeHistory())
    }

    @Test
    fun fresh_install_has_no_history() {
        assertEquals(emptyList(), manager().currentCustomThemeHistory())
    }
}
