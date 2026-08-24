package whl.trending.ai.ui.theme

import com.materialkolor.PaletteStyle
import com.materialkolor.hct.Hct
import org.jetbrains.compose.resources.StringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.theme_color_berry
import trendingai.shared.generated.resources.theme_color_brown
import trendingai.shared.generated.resources.theme_color_default
import trendingai.shared.generated.resources.theme_color_forest
import trendingai.shared.generated.resources.theme_color_graphite
import trendingai.shared.generated.resources.theme_color_indigo
import trendingai.shared.generated.resources.theme_color_mustard
import trendingai.shared.generated.resources.theme_color_olive
import trendingai.shared.generated.resources.theme_color_peacock
import trendingai.shared.generated.resources.theme_color_pine
import trendingai.shared.generated.resources.theme_color_plum
import trendingai.shared.generated.resources.theme_color_rust
import trendingai.shared.generated.resources.theme_color_slate
import trendingai.shared.generated.resources.theme_color_steel
import whl.trending.ai.data.local.DEFAULT_SEED_ARGB

data class ThemeSeed(
    val id: String,
    val nameRes: StringResource,
    val argb: Long,
    // colorScheme 生成算法风格：彩色档统一 TonalSpot，中性档 Neutral / Monochrome——
    // 避免低彩度 seed 被 TonalSpot 拉高彩度，色卡与实际配色对不上。
    val style: PaletteStyle = PaletteStyle.TonalSpot,
)

/**
 * 色卡的统一彩度与明度，取自品牌紫 `6750A4` 的 HCT 坐标——默认档不必改色值也能融进色板。
 */
private const val SWATCH_CHROMA = 48.0
private const val SWATCH_TONE = 44.0

/** 11 个彩色档的色相：以品牌紫 298° 为锚，沿色相环均匀铺开（间隔 32.7°） */
private const val HUE_STEP = 32.73

private fun hue(index: Int): Double = (298.0 + HUE_STEP * index) % 360.0

/** 按 HCT 坐标生成不透明 seed */
private fun seed(hue: Double, chroma: Double = SWATCH_CHROMA, tone: Double = SWATCH_TONE): Long =
    Hct.from(hue, chroma, tone).toInt().toLong() and 0xFFFFFFFFL

/**
 * 14 色预设：11 个彩色沿色相环连续递增 + 3 档中性收尾，加自定义档正好 5×3 满排。
 * 彩度/明度全部归一、只有色相在变，色板才读成一条连续色带。
 */
val PRESET_PALETTE: List<ThemeSeed> = listOf(
    // 品牌紫保持原色值：它是 app 图标色，也是所有存量用户的默认档，不为色板整齐而改
    ThemeSeed("default",  Res.string.theme_color_default,  DEFAULT_SEED_ARGB),
    ThemeSeed("plum",     Res.string.theme_color_plum,     seed(hue(1))),   // 331° 梅红
    ThemeSeed("berry",    Res.string.theme_color_berry,    seed(hue(2))),   //   4° 莓红
    ThemeSeed("rust",     Res.string.theme_color_rust,     seed(hue(3))),   //  36° 赭红
    ThemeSeed("mustard",  Res.string.theme_color_mustard,  seed(hue(4))),   //  69° 芥末
    ThemeSeed("olive",    Res.string.theme_color_olive,    seed(hue(5))),   // 102° 橄榄
    ThemeSeed("forest",   Res.string.theme_color_forest,   seed(hue(6))),   // 134° 森绿
    ThemeSeed("pine",     Res.string.theme_color_pine,     seed(hue(7))),   // 167° 松绿
    ThemeSeed("peacock",  Res.string.theme_color_peacock,  seed(hue(8))),   // 200° 孔雀
    ThemeSeed("steel",    Res.string.theme_color_steel,    seed(hue(9))),   // 233° 钢蓝
    ThemeSeed("indigo",   Res.string.theme_color_indigo,   seed(hue(10))),   // 265° 靛蓝
    // 中性三档：同一 tone、靠低彩度与彩色档拉开；石墨再压暗一档做纯黑锚点。
    ThemeSeed("brown",    Res.string.theme_color_brown,    seed(40.0, chroma = 16.0), PaletteStyle.Neutral),
    ThemeSeed("slate",    Res.string.theme_color_slate,    seed(230.0, chroma = 14.0), PaletteStyle.Neutral),
    ThemeSeed("graphite", Res.string.theme_color_graphite, seed(280.0, chroma = 2.0, tone = 30.0), PaletteStyle.Monochrome),
)
