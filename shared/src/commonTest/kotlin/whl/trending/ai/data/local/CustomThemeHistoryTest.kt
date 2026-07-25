package whl.trending.ai.data.local

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
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
        assertEquals(10, history.size)
        // 留下的是最近 10 条，最新在前
        assertEquals(0xFF00000BL, history.first().seedArgb)
        assertEquals(0xFF000002L, history.last().seedArgb)
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
