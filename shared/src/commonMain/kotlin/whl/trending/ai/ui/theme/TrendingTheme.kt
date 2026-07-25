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
import whl.trending.ai.data.local.DEFAULT_THEME_CONTRAST_STORAGE
import whl.trending.ai.data.local.DEFAULT_THEME_STYLE_STORAGE
import whl.trending.ai.data.local.ThemeMode
import whl.trending.ai.data.local.globalSettingsManager

@Composable
fun TrendingTheme(content: @Composable () -> Unit) {
    val initialMode = remember { globalSettingsManager.currentThemeMode() }
    val initialSeed = remember {
        // 在读取任何主题字段之前先跑一次遗留迁移：预设表由这里传进去，
        // 免得 data 层为了判断「是不是预设色」反向依赖 ui 层。
        globalSettingsManager.migrateLegacySeedIfNeeded(PRESET_PALETTE.map { it.argb }.toSet())
        globalSettingsManager.currentSeedColor()
    }
    val initialCustom = remember { globalSettingsManager.currentThemeCustom() }
    val initialStyle = remember { globalSettingsManager.currentThemeStyle() }
    val initialContrast = remember { globalSettingsManager.currentThemeContrast() }

    val themeMode by globalSettingsManager.themeMode.collectAsState(initialMode)
    val seedArgb by globalSettingsManager.seedColor.collectAsState(initialSeed)
    val isCustom by globalSettingsManager.themeCustom.collectAsState(initialCustom)
    val styleStorage by globalSettingsManager.themeStyle.collectAsState(initialStyle)
    val contrastStorage by globalSettingsManager.themeContrast.collectAsState(initialContrast)

    val isDark = when (themeMode) {
        ThemeMode.FOLLOW_SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    // 自定义档用用户在调色台里存的风格/对比度；预设档用 PRESET_PALETTE 钦定的搭配
    // （对比度固定标准档），保证「点回预设」永远能恢复我们设计的观感。
    val resolved = remember(seedArgb, isCustom, styleStorage, contrastStorage) {
        resolveThemeConfig(seedArgb, isCustom, styleStorage, contrastStorage)
    }

    val state = rememberDynamicMaterialThemeState(
        seedColor = Color(seedArgb),
        isDark = isDark,
        style = resolved.style.style,
        contrastLevel = resolved.contrast.level,
        // M3 Expressive 的 2025 色规：同一 seed 出来的配色更饱和、层次更强。
        // 库默认仍是 SPEC_2021（偏柔和收敛），这里全局显式切到 2025，不作为可调项暴露。
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

/**
 * 把持久化的四个字段解析成一组生效配置。抽成纯函数便于单测覆盖预设/自定义两条分支。
 *
 * 预设档查 [PRESET_PALETTE] 拿钦定 style；查不到（历史版本残留的旧色值）回落柔和 + 标准对比度。
 */
internal fun resolveThemeConfig(
    seedArgb: Long,
    isCustom: Boolean,
    styleStorage: String = DEFAULT_THEME_STYLE_STORAGE,
    contrastStorage: String = DEFAULT_THEME_CONTRAST_STORAGE,
): ThemeConfig = if (isCustom) {
    ThemeConfig(
        seedArgb = seedArgb,
        style = ThemeStyleOption.fromStorage(styleStorage),
        contrast = ThemeContrastOption.fromStorage(contrastStorage),
    )
} else {
    val preset = PRESET_PALETTE.find { it.argb == seedArgb }
    ThemeConfig(
        seedArgb = seedArgb,
        style = ThemeStyleOption.fromStyle(preset?.style ?: PaletteStyle.TonalSpot),
        contrast = ThemeContrastOption.STANDARD,
    )
}
