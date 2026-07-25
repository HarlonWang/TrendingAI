package whl.trending.ai.ui.theme

import com.materialkolor.PaletteStyle
import org.jetbrains.compose.resources.StringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.theme_color_amber
import trendingai.shared.generated.resources.theme_color_blue
import trendingai.shared.generated.resources.theme_color_brown
import trendingai.shared.generated.resources.theme_color_crimson
import trendingai.shared.generated.resources.theme_color_cyan
import trendingai.shared.generated.resources.theme_color_default
import trendingai.shared.generated.resources.theme_color_graphite
import trendingai.shared.generated.resources.theme_color_green
import trendingai.shared.generated.resources.theme_color_indigo
import trendingai.shared.generated.resources.theme_color_lime
import trendingai.shared.generated.resources.theme_color_orange
import trendingai.shared.generated.resources.theme_color_pink
import trendingai.shared.generated.resources.theme_color_slate
import trendingai.shared.generated.resources.theme_color_teal
import whl.trending.ai.data.local.DEFAULT_SEED_ARGB

data class ThemeSeed(
    val id: String,
    val nameRes: StringResource,
    val argb: Long,
    // 生成整套 colorScheme 的算法风格。默认 TonalSpot（M3 标准、柔和收敛），
    // 个别预设覆盖为更有性格的风格，让各预设彼此对比拉满：
    //   Fidelity  → primary 忠实还原原始 seed 色（红更正、不发粉）
    //   Vibrant   → 保留高饱和，强调色更鲜艳抢眼
    //   Monochrome→ 整套压成灰阶，做黑/灰中性档
    val style: PaletteStyle = PaletteStyle.TonalSpot,
)

/**
 * 14 色预设，排布是「品牌紫打头 → 彩色按色相环走一圈 → 三档中性收尾」，
 * 加上末尾的自定义档正好 5×3 满排。
 *
 * 冷色区 cyan(201°)/blue(210°) 只差 9° 色相，观感确实接近——保留是因为埋点显示
 * 青蓝系合计占改色用户 35%，且全量收录后所有历史色值都在表内，老用户升级零感知。
 * 棕(15°)、蓝灰(199°) 靠低饱和与同色相区的高饱和档拉开，不是靠色相。
 */
val PRESET_PALETTE: List<ThemeSeed> = listOf(
    ThemeSeed("default",  Res.string.theme_color_default,  DEFAULT_SEED_ARGB),                     // 品牌紫，TonalSpot 基线
    ThemeSeed("crimson",  Res.string.theme_color_crimson,  0xFFDC362EL, PaletteStyle.Fidelity),    // 朱砂，忠实还原
    ThemeSeed("orange",   Res.string.theme_color_orange,   0xFFF4511EL, PaletteStyle.Vibrant),     // 橙
    ThemeSeed("amber",    Res.string.theme_color_amber,    0xFFFFB300L, PaletteStyle.Vibrant),     // 琥珀
    ThemeSeed("lime",     Res.string.theme_color_lime,     0xFF689F38L, PaletteStyle.Vibrant),     // 青柠
    ThemeSeed("green",    Res.string.theme_color_green,    0xFF2E7D32L, PaletteStyle.Vibrant),     // 森绿
    ThemeSeed("teal",     Res.string.theme_color_teal,     0xFF00897BL, PaletteStyle.Vibrant),     // 青绿
    ThemeSeed("cyan",     Res.string.theme_color_cyan,     0xFF0288D1L, PaletteStyle.Vibrant),     // 青蓝
    ThemeSeed("blue",     Res.string.theme_color_blue,     0xFF1976D2L, PaletteStyle.Vibrant),     // 海蓝
    ThemeSeed("indigo",   Res.string.theme_color_indigo,   0xFF3F51B5L, PaletteStyle.Vibrant),     // 靛蓝
    ThemeSeed("pink",     Res.string.theme_color_pink,     0xFFC2185BL, PaletteStyle.Fidelity),    // 玫红，忠实还原
    ThemeSeed("brown",    Res.string.theme_color_brown,    0xFF6D4C41L),                           // 棕，低饱和暖中性
    ThemeSeed("slate",    Res.string.theme_color_slate,    0xFF546E7AL),                           // 蓝灰，低饱和冷中性
    ThemeSeed("graphite", Res.string.theme_color_graphite, 0xFF3A3A3CL, PaletteStyle.Monochrome),  // 石墨灰黑，纯灰阶
)
