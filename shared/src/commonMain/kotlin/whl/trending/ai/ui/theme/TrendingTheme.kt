package whl.trending.ai.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.materialkolor.DynamicMaterialTheme
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicMaterialThemeState
import whl.trending.ai.data.local.ThemeMode
import whl.trending.ai.data.local.globalSettingsManager

@Composable
fun TrendingTheme(content: @Composable () -> Unit) {
    val initialMode = remember { globalSettingsManager.currentThemeMode() }
    val initialSeed = remember { globalSettingsManager.currentSeedColor() }
    val themeMode by globalSettingsManager.themeMode.collectAsState(initialMode)
    val seedArgb by globalSettingsManager.seedColor.collectAsState(initialSeed)

    val isDark = when (themeMode) {
        ThemeMode.FOLLOW_SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    // 持久化里只存了 seed 的 ARGB，用它反查预设的算法风格；
    // 非预设色（自定义 seed）或查不到时回落 TonalSpot。
    // PRESET_PALETTE 的 argb 唯一（有单测保证），反查不会撞。
    val style = remember(seedArgb) {
        PRESET_PALETTE.find { it.argb == seedArgb }?.style ?: PaletteStyle.TonalSpot
    }

    val state = rememberDynamicMaterialThemeState(
        seedColor = Color(seedArgb),
        isDark = isDark,
        style = style,
        // M3 Expressive 的 2025 色规：同一 seed 出来的配色更饱和、层次更强。
        // 库默认仍是 SPEC_2021（偏柔和收敛），这里显式切到 2025。
        specVersion = ColorSpec.SpecVersion.SPEC_2025,
    )

    DynamicMaterialTheme(
        state = state,
        animate = true,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
    ) {
        content()
    }
}
