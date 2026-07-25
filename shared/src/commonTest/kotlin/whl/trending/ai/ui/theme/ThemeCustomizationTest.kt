package whl.trending.ai.ui.theme

import com.materialkolor.PaletteStyle
import whl.trending.ai.data.local.DEFAULT_SEED_ARGB
import whl.trending.ai.data.local.DEFAULT_THEME_CONTRAST_STORAGE
import whl.trending.ai.data.local.DEFAULT_THEME_STYLE_STORAGE
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ThemeCustomizationTest {

    // ---- 风格档位 ----

    @Test
    fun style_options_cover_every_preset_style() {
        // 调色台的风格档必须覆盖预设用到的全部 PaletteStyle，
        // 否则从某个预设 fork 进调色台时会对不上号、被静默改成别的风格
        PRESET_PALETTE.forEach { seed ->
            assertEquals(
                seed.style,
                ThemeStyleOption.fromStyle(seed.style).style,
                "预设 ${seed.id} 的 style ${seed.style} 在调色台里没有对应档位",
            )
        }
    }

    @Test
    fun style_storage_round_trips() {
        ThemeStyleOption.entries.forEach { option ->
            assertEquals(option, ThemeStyleOption.fromStorage(option.storageValue))
        }
    }

    @Test
    fun style_storage_falls_back_on_unknown_value() {
        assertEquals(ThemeStyleOption.SOFT, ThemeStyleOption.fromStorage("nope"))
        assertEquals(ThemeStyleOption.SOFT, ThemeStyleOption.fromStorage(null))
    }

    @Test
    fun style_default_storage_constant_matches_enum() {
        // SettingsManager 里用字面量避免反向依赖 UI 层，这里守住两侧一致
        assertEquals(ThemeStyleOption.SOFT, ThemeStyleOption.fromStorage(DEFAULT_THEME_STYLE_STORAGE))
        assertEquals(DEFAULT_THEME_STYLE_STORAGE, ThemeStyleOption.SOFT.storageValue)
    }

    // ---- 对比度档位 ----

    @Test
    fun contrast_storage_round_trips() {
        ThemeContrastOption.entries.forEach { option ->
            assertEquals(option, ThemeContrastOption.fromStorage(option.storageValue))
        }
    }

    @Test
    fun contrast_default_storage_constant_matches_enum() {
        assertEquals(
            ThemeContrastOption.STANDARD,
            ThemeContrastOption.fromStorage(DEFAULT_THEME_CONTRAST_STORAGE),
        )
        assertEquals(DEFAULT_THEME_CONTRAST_STORAGE, ThemeContrastOption.STANDARD.storageValue)
    }

    @Test
    fun contrast_levels_stay_in_supported_range() {
        // materialkolor 的 contrastLevel 定义域是 -1.0..1.0
        ThemeContrastOption.entries.forEach { option ->
            assertTrue(option.level in 0.0..1.0, "${option.storageValue} 的 level 越界")
        }
    }

    // ---- hex 解析 ----

    @Test
    fun parses_hex_with_and_without_hash() {
        assertEquals(0xFF6750A4L, parseHexColor("#6750A4"))
        assertEquals(0xFF6750A4L, parseHexColor("6750A4"))
        assertEquals(0xFF6750A4L, parseHexColor("  6750a4  "))
    }

    @Test
    fun rejects_malformed_hex() {
        assertNull(parseHexColor(""))
        assertNull(parseHexColor("6750A"))      // 位数不足
        assertNull(parseHexColor("6750A4F"))    // 位数过多
        assertNull(parseHexColor("GGGGGG"))     // 非十六进制
        assertNull(parseHexColor("FF6750A4"))   // 8 位带 alpha 不接受：seed 必须不透明
    }

    @Test
    fun formats_hex_uppercase_and_padded() {
        assertEquals("6750A4", formatHexColor(0xFF6750A4L))
        assertEquals("0000FF", formatHexColor(0xFF0000FFL))
        assertEquals("000000", formatHexColor(0xFF000000L))
    }

    @Test
    fun hex_round_trips_through_parse_and_format() {
        listOf(0xFF6750A4L, 0xFFDC362EL, 0xFFFFB300L, 0xFF2E7D32L, 0xFF1976D2L, 0xFF3A3A3CL)
            .forEach { argb ->
                val parsed = parseHexColor(formatHexColor(argb))
                assertEquals(argb, parsed)
            }
    }

    // ---- HSV 转换 ----

    @Test
    fun hsv_round_trips_for_all_presets() {
        // 取色面板拖动前会把已有 seed 反填成 HSV，转换必须无损（±1 量化误差）
        PRESET_PALETTE.forEach { seed ->
            val back = hsvToArgb(argbToHsv(seed.argb))
            assertChannelsClose(seed.argb, back, tolerance = 1)
        }
    }

    @Test
    fun hsv_handles_pure_colors() {
        assertEquals(0f, argbToHsv(0xFFFF0000L).hue)
        assertEquals(120f, argbToHsv(0xFF00FF00L).hue)
        assertEquals(240f, argbToHsv(0xFF0000FFL).hue)
    }

    @Test
    fun hsv_handles_achromatic_colors() {
        val white = argbToHsv(0xFFFFFFFFL)
        assertEquals(0f, white.saturation)
        assertEquals(1f, white.value)

        val black = argbToHsv(0xFF000000L)
        assertEquals(0f, black.saturation)
        assertEquals(0f, black.value)
    }

    @Test
    fun hsv_to_argb_clamps_out_of_range_input() {
        // 手指拖出面板时会传入越界值，应钳制而不是产生异常颜色
        val clamped = hsvToArgb(Hsv(hue = 400f, saturation = 1.5f, value = -0.2f))
        assertEquals(0xFF000000L, clamped)
        assertTrue(hsvToArgb(Hsv(-60f, 1f, 1f)) != 0L)
    }

    @Test
    fun hsv_output_is_always_opaque() {
        val argb = hsvToArgb(Hsv(210f, 0.5f, 0.5f))
        assertEquals(0xFF000000L, argb and 0xFF000000L)
    }

    // ---- 生效配置解析 ----

    @Test
    fun preset_config_uses_palette_style_and_standard_contrast() {
        PRESET_PALETTE.forEach { seed ->
            val config = resolveThemeConfig(seed.argb, isCustom = false)
            assertEquals(seed.style, config.style.style, "预设 ${seed.id} 的风格未按调色板还原")
            assertEquals(ThemeContrastOption.STANDARD, config.contrast)
        }
    }

    @Test
    fun preset_config_ignores_stored_custom_values() {
        // 关键语义：选中预设时，用户上次在调色台里存的风格/对比度不得渗透进来
        val config = resolveThemeConfig(
            seedArgb = DEFAULT_SEED_ARGB,
            isCustom = false,
            styleStorage = ThemeStyleOption.MONO.storageValue,
            contrastStorage = ThemeContrastOption.HIGH.storageValue,
        )
        assertEquals(ThemeStyleOption.SOFT, config.style)
        assertEquals(ThemeContrastOption.STANDARD, config.contrast)
    }

    @Test
    fun custom_config_uses_stored_values() {
        val config = resolveThemeConfig(
            seedArgb = 0xFF123456L,
            isCustom = true,
            styleStorage = ThemeStyleOption.BOLD.storageValue,
            contrastStorage = ThemeContrastOption.HIGH.storageValue,
        )
        assertEquals(0xFF123456L, config.seedArgb)
        assertEquals(ThemeStyleOption.BOLD, config.style)
        assertEquals(ThemeContrastOption.HIGH, config.contrast)
    }

    @Test
    fun custom_config_survives_seed_colliding_with_preset() {
        // 用户完全可能把自定义色调到与某个预设一模一样；
        // 此时仍必须走自定义分支，否则他调的风格/对比度会被预设覆盖掉
        val preset = PRESET_PALETTE.first { it.style != PaletteStyle.Monochrome }
        val config = resolveThemeConfig(
            seedArgb = preset.argb,
            isCustom = true,
            styleStorage = ThemeStyleOption.MONO.storageValue,
            contrastStorage = ThemeContrastOption.HIGH.storageValue,
        )
        assertEquals(ThemeStyleOption.MONO, config.style)
        assertEquals(ThemeContrastOption.HIGH, config.contrast)
    }

    @Test
    fun unknown_preset_seed_falls_back_to_soft() {
        // 预设表外的色值（历史残留、或用户手改配置）不该让主题崩成异常配置。
        // 当前预设表已收录全部历史色，这里用一个从未做过预设的色值来守这条回落路径。
        val config = resolveThemeConfig(0xFF00BCD4L, isCustom = false)
        assertEquals(ThemeStyleOption.SOFT, config.style)
        assertEquals(ThemeContrastOption.STANDARD, config.contrast)
    }

    @Test
    fun default_seed_resolves_to_default_preset() {
        val config = resolveThemeConfig(DEFAULT_SEED_ARGB, isCustom = false)
        val defaultPreset = PRESET_PALETTE.first()
        assertEquals(defaultPreset.argb, config.seedArgb)
        assertNotNull(PRESET_PALETTE.find { it.argb == DEFAULT_SEED_ARGB })
    }

    private fun assertChannelsClose(expected: Long, actual: Long, tolerance: Int) {
        listOf(16, 8, 0).forEach { shift ->
            val e = ((expected shr shift) and 0xFF).toInt()
            val a = ((actual shr shift) and 0xFF).toInt()
            assertTrue(
                abs(e - a) <= tolerance,
                "通道差异过大：expected=${formatHexColor(expected)} actual=${formatHexColor(actual)}",
            )
        }
    }
}
