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
    // 生成整套 colorScheme 的算法风格。默认 TonalSpot（M3 标准、柔和收敛），
    // 个别预设覆盖为更有性格的风格，让各预设彼此对比拉满：
    //   Vibrant   → 保留高饱和，强调色更鲜艳抢眼
    //   Monochrome→ 整套压成灰阶，做黑/灰中性档
    val style: PaletteStyle = PaletteStyle.TonalSpot,
)

/**
 * 色卡的统一彩度与明度，取自品牌紫 `6750A4` 的 HCT 坐标（chroma 47 / tone 40），
 * 这样默认档不必改色值也能融进色板。
 *
 * 归一化的必要性：直接拿 M2 色板的现成色值（600/700/800 档混用）拼出来的色卡，
 * tone 会从 24 铺到 78、chroma 从 2 到 83，一排看过去像从不同调色盘各抓一把。
 * HCT 是 M3 配色算法的底层色彩空间，按人眼感知校准过——同一 tone 下黄色和蓝色
 * 看起来一样亮，HSV 做不到这点。
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
 * 14 色预设：11 个彩色沿色相环连续递增（从品牌紫起绕一圈）+ 3 档中性收尾，
 * 加上末尾的自定义档正好 5×3 满排。
 *
 * 彩度/明度全部归一，只有色相在变，所以整块色板读起来是一条连续色带而不是一把杂色。
 * 色名按归一化后的实际观感重定（tone 44 偏沉，所以是梅红/赭红/芥末这类沉稳色名，
 * 而不是原来照搬 M2 亮色起的朱砂/琥珀/青柠）。
 * 中性三档共用同一 tone、靠低彩度自成一组；石墨再压暗一档做纯黑锚点。
 */
val PRESET_PALETTE: List<ThemeSeed> = listOf(
    // 品牌紫保持原色值：它是 app 图标色，也是所有存量用户的默认档，不为色板整齐而改
    ThemeSeed("default",  Res.string.theme_color_default,  DEFAULT_SEED_ARGB),
    ThemeSeed("plum",     Res.string.theme_color_plum,     seed(hue(1)),  PaletteStyle.Vibrant),   // 331° 梅红
    ThemeSeed("berry",    Res.string.theme_color_berry,    seed(hue(2)),  PaletteStyle.Vibrant),   //   4° 莓红
    ThemeSeed("rust",     Res.string.theme_color_rust,     seed(hue(3)),  PaletteStyle.Vibrant),   //  36° 赭红
    ThemeSeed("mustard",  Res.string.theme_color_mustard,  seed(hue(4)),  PaletteStyle.Vibrant),   //  69° 芥末
    ThemeSeed("olive",    Res.string.theme_color_olive,    seed(hue(5)),  PaletteStyle.Vibrant),   // 102° 橄榄
    ThemeSeed("forest",   Res.string.theme_color_forest,   seed(hue(6)),  PaletteStyle.Vibrant),   // 134° 森绿
    ThemeSeed("pine",     Res.string.theme_color_pine,     seed(hue(7)),  PaletteStyle.Vibrant),   // 167° 松绿
    ThemeSeed("peacock",  Res.string.theme_color_peacock,  seed(hue(8)),  PaletteStyle.Vibrant),   // 200° 孔雀
    ThemeSeed("steel",    Res.string.theme_color_steel,    seed(hue(9)),  PaletteStyle.Vibrant),   // 233° 钢蓝
    ThemeSeed("indigo",   Res.string.theme_color_indigo,   seed(hue(10)), PaletteStyle.Vibrant),   // 265° 靛蓝
    // 中性三档：同一 tone，靠低彩度与彩色档拉开，暖(棕)/冷(蓝灰)/纯灰(石墨) 各一
    ThemeSeed("brown",    Res.string.theme_color_brown,    seed(40.0, chroma = 16.0)),
    ThemeSeed("slate",    Res.string.theme_color_slate,    seed(230.0, chroma = 14.0)),
    ThemeSeed("graphite", Res.string.theme_color_graphite, seed(280.0, chroma = 2.0, tone = 30.0), PaletteStyle.Monochrome),
)
