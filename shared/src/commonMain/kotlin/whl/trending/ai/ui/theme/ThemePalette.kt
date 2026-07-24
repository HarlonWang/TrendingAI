package whl.trending.ai.ui.theme

import com.materialkolor.PaletteStyle
import org.jetbrains.compose.resources.StringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.theme_color_amber
import trendingai.shared.generated.resources.theme_color_blue
import trendingai.shared.generated.resources.theme_color_crimson
import trendingai.shared.generated.resources.theme_color_default
import trendingai.shared.generated.resources.theme_color_graphite
import trendingai.shared.generated.resources.theme_color_green
import whl.trending.ai.data.local.DEFAULT_SEED_ARGB

data class ThemeSeed(
    val id: String,
    val nameRes: StringResource,
    val argb: Long,
    // 生成整套 colorScheme 的算法风格。默认 TonalSpot（M3 标准、柔和收敛），
    // 个别预设覆盖为更有性格的风格，让 6 个预设彼此对比拉满：
    //   Fidelity  → primary 忠实还原原始 seed 色（红更正、不发粉）
    //   Vibrant   → 保留高饱和，强调色更鲜艳抢眼
    //   Monochrome→ 整套压成灰阶，做黑/灰中性档
    val style: PaletteStyle = PaletteStyle.TonalSpot,
)

// 6 色预设：紫→红→黄→绿→蓝 五档色相均匀铺开 + 一档石墨灰黑中性，两两对比明显。
val PRESET_PALETTE: List<ThemeSeed> = listOf(
    ThemeSeed("default",  Res.string.theme_color_default,  DEFAULT_SEED_ARGB),                     // TonalSpot 基线（品牌紫）
    ThemeSeed("crimson",  Res.string.theme_color_crimson,  0xFFDC362EL, PaletteStyle.Fidelity),    // 朱红，忠实还原
    ThemeSeed("amber",    Res.string.theme_color_amber,    0xFFFFB300L, PaletteStyle.Vibrant),     // 琥珀，暖黄
    ThemeSeed("green",    Res.string.theme_color_green,    0xFF2E7D32L, PaletteStyle.Vibrant),     // 森绿
    ThemeSeed("blue",     Res.string.theme_color_blue,     0xFF1976D2L, PaletteStyle.Vibrant),     // 海蓝
    ThemeSeed("graphite", Res.string.theme_color_graphite, 0xFF3A3A3CL, PaletteStyle.Monochrome),  // 石墨灰黑，纯灰阶
)
