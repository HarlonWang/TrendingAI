package whl.trending.ai.data.local

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 调色台是实时生效的，按返回键不会撤销——「撤销修改」按进入时的快照整体写回，
 * 是用户调坏之后唯一的退路。这些用例守住"写回去的确实是进来时那套"。
 */
class ThemeSnapshotTest {

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
    fun discard_restores_a_preset_entry_state() {
        // 从预设档进来，调了一通再撤销：应当回到那个预设，而不是默认紫
        val preset = 0xFF497322L
        val m = manager("prefs_seed_color" to preset, "prefs_theme_custom" to false)
        val snapshot = m.currentThemeSnapshot()

        m.setCustomTheme(0xFFD219ABL, "vivid", "high")
        m.restoreThemeSnapshot(snapshot)

        assertEquals(preset, m.currentSeedColor())
        assertFalse(m.currentThemeCustom())
        // 本次编辑产生的自定义色不该留下，否则外观页末尾那颗圆显示的是已被撤销的颜色
        assertNull(m.currentCustomSeedColor())
    }

    @Test
    fun discard_restores_a_custom_entry_state() {
        // 从自定义档进来，再调再撤销：应当回到进来时那个自定义色与风格
        val m = manager()
        m.setCustomTheme(0xFF112233L, "muted", "medium")
        val snapshot = m.currentThemeSnapshot()

        m.setCustomTheme(0xFFD219ABL, "vivid", "high")
        m.restoreThemeSnapshot(snapshot)

        assertEquals(0xFF112233L, m.currentSeedColor())
        assertEquals(0xFF112233L, m.currentCustomSeedColor())
        assertTrue(m.currentThemeCustom())
        assertEquals("muted", m.currentThemeStyle())
        assertEquals("medium", m.currentThemeContrast())
    }

    @Test
    fun snapshot_round_trips_every_field() {
        val m = manager()
        m.setCustomTheme(0xFFABCDEFL, "bold", "high")
        val snapshot = m.currentThemeSnapshot()

        assertEquals(
            ThemeSnapshot(0xFFABCDEFL, true, "bold", "high", 0xFFABCDEFL),
            snapshot,
        )
    }

    @Test
    fun discard_does_not_touch_history() {
        // 撤销掉的配置不该进历史——它正是用户表示"我不要这次改动"
        val m = manager()
        m.pushCustomThemeHistory(CustomThemeEntry(0xFF112233L))
        val snapshot = m.currentThemeSnapshot()

        m.setCustomTheme(0xFFD219ABL, "vivid", "high")
        m.restoreThemeSnapshot(snapshot)

        assertEquals(listOf(CustomThemeEntry(0xFF112233L)), m.currentCustomThemeHistory())
    }
}
