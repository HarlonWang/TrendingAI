package whl.trending.ai.ui.theme

import com.materialkolor.PaletteStyle
import org.jetbrains.compose.resources.StringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.color_lab_contrast_high
import trendingai.shared.generated.resources.color_lab_contrast_medium
import trendingai.shared.generated.resources.color_lab_contrast_standard
import trendingai.shared.generated.resources.color_lab_style_bold
import trendingai.shared.generated.resources.color_lab_style_faithful
import trendingai.shared.generated.resources.color_lab_style_mono
import trendingai.shared.generated.resources.color_lab_style_soft
import trendingai.shared.generated.resources.color_lab_style_vivid
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 调色台暴露给用户的「风格」档位——把 materialkolor 的算法名翻译成大白话。
 *
 * 必须覆盖 [PRESET_PALETTE] 用到的全部 [PaletteStyle]（有单测保证），
 * 否则从预设 fork 进调色台时会有档位对不上号、被迫回落成别的风格。
 */
enum class ThemeStyleOption(
    val storageValue: String,
    val style: PaletteStyle,
    val labelRes: StringResource,
) {
    SOFT("soft", PaletteStyle.TonalSpot, Res.string.color_lab_style_soft),
    VIVID("vivid", PaletteStyle.Vibrant, Res.string.color_lab_style_vivid),
    BOLD("bold", PaletteStyle.Expressive, Res.string.color_lab_style_bold),
    FAITHFUL("faithful", PaletteStyle.Fidelity, Res.string.color_lab_style_faithful),
    MONO("mono", PaletteStyle.Monochrome, Res.string.color_lab_style_mono),
    ;

    companion object {
        /** 持久化值 → 档位；未知值（降级安装、手改配置）回落柔和 */
        fun fromStorage(value: String?): ThemeStyleOption =
            entries.firstOrNull { it.storageValue == value } ?: SOFT

        /** PaletteStyle → 档位；用于从预设 fork 时预填 */
        fun fromStyle(style: PaletteStyle): ThemeStyleOption =
            entries.firstOrNull { it.style == style } ?: SOFT
    }
}

/**
 * 对比度档位。materialkolor 的 contrastLevel 取值域是 -1.0..1.0，
 * 这里只暴露 0 及以上的三档——负值会把配色压得比标准还低，无障碍上没有意义。
 */
enum class ThemeContrastOption(
    val storageValue: String,
    val level: Double,
    val labelRes: StringResource,
) {
    STANDARD("standard", 0.0, Res.string.color_lab_contrast_standard),
    MEDIUM("medium", 0.5, Res.string.color_lab_contrast_medium),
    HIGH("high", 1.0, Res.string.color_lab_contrast_high),
    ;

    companion object {
        fun fromStorage(value: String?): ThemeContrastOption =
            entries.firstOrNull { it.storageValue == value } ?: STANDARD
    }
}

/**
 * 调色台的一组完整配置。自定义档存的就是它，预设档则由 [PRESET_PALETTE] 推导出等价值。
 */
data class ThemeConfig(
    val seedArgb: Long,
    val style: ThemeStyleOption,
    val contrast: ThemeContrastOption,
)

/** HSV 三元组：hue 0..360，saturation/value 0..1 */
data class Hsv(val hue: Float, val saturation: Float, val value: Float)

/**
 * `#RRGGBB` / `RRGGBB` → 不透明 ARGB（Long）。大小写、首尾空白、`#` 前缀都容忍；
 * 位数不对或含非十六进制字符时返回 null，交由调用方提示。
 *
 * 只接受 6 位：8 位带 alpha 的色值对 seed 没有意义（seed 必须不透明）。
 */
fun parseHexColor(input: String): Long? {
    val hex = input.trim().removePrefix("#")
    if (hex.length != 6) return null
    if (!hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
    val rgb = hex.toLongOrNull(16) ?: return null
    return 0xFF000000L or rgb
}

/** ARGB → 6 位大写 hex（不含 `#`），alpha 位丢弃 */
fun formatHexColor(argb: Long): String {
    val rgb = argb and 0xFFFFFFL
    return rgb.toString(16).uppercase().padStart(6, '0')
}

/** ARGB → HSV。用于把已有 seed 反填到取色面板的光标位置 */
fun argbToHsv(argb: Long): Hsv {
    val r = ((argb shr 16) and 0xFF).toFloat() / 255f
    val g = ((argb shr 8) and 0xFF).toFloat() / 255f
    val b = (argb and 0xFF).toFloat() / 255f

    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min

    val hue = when {
        delta == 0f -> 0f
        max == r -> 60f * (((g - b) / delta) % 6f)
        max == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }.let { if (it < 0f) it + 360f else it }

    val saturation = if (max == 0f) 0f else delta / max
    return Hsv(hue, saturation, max)
}

/** HSV → 不透明 ARGB */
fun hsvToArgb(hsv: Hsv): Long {
    val h = ((hsv.hue % 360f) + 360f) % 360f
    val s = hsv.saturation.coerceIn(0f, 1f)
    val v = hsv.value.coerceIn(0f, 1f)

    val c = v * s
    val x = c * (1f - abs((h / 60f) % 2f - 1f))
    val m = v - c

    val (r1, g1, b1) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }

    val r = ((r1 + m) * 255f).roundToInt().toLong()
    val g = ((g1 + m) * 255f).roundToInt().toLong()
    val b = ((b1 + m) * 255f).roundToInt().toLong()
    return 0xFF000000L or (r shl 16) or (g shl 8) or b
}
