package whl.trending.ai.ui.theme

import org.jetbrains.compose.resources.StringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.theme_color_amber
import trendingai.shared.generated.resources.theme_color_blue
import trendingai.shared.generated.resources.theme_color_crimson
import trendingai.shared.generated.resources.theme_color_cyan
import trendingai.shared.generated.resources.theme_color_default
import trendingai.shared.generated.resources.theme_color_green
import trendingai.shared.generated.resources.theme_color_indigo
import trendingai.shared.generated.resources.theme_color_orange
import trendingai.shared.generated.resources.theme_color_pink
import trendingai.shared.generated.resources.theme_color_teal
import whl.trending.ai.data.local.DEFAULT_SEED_ARGB

data class ThemeSeed(
    val id: String,
    val nameRes: StringResource,
    val argb: Long,
)

val PRESET_PALETTE: List<ThemeSeed> = listOf(
    ThemeSeed("default", Res.string.theme_color_default, DEFAULT_SEED_ARGB),
    ThemeSeed("crimson", Res.string.theme_color_crimson, 0xFFDC362EL), // M3 baseline error red
    ThemeSeed("orange",  Res.string.theme_color_orange,  0xFFF4511EL), // M2 Deep Orange 600
    ThemeSeed("amber",   Res.string.theme_color_amber,   0xFFFFB300L), // M2 Amber 600
    ThemeSeed("green",   Res.string.theme_color_green,   0xFF2E7D32L), // M2 Green 800
    ThemeSeed("teal",    Res.string.theme_color_teal,    0xFF00897BL), // M2 Teal 600
    ThemeSeed("cyan",    Res.string.theme_color_cyan,    0xFF0288D1L), // M2 Light Blue 700
    ThemeSeed("blue",    Res.string.theme_color_blue,    0xFF1976D2L), // M2 Blue 700
    ThemeSeed("indigo",  Res.string.theme_color_indigo,  0xFF3F51B5L), // M2 Indigo 500
    ThemeSeed("pink",    Res.string.theme_color_pink,    0xFFC2185BL), // M2 Pink 700
)
