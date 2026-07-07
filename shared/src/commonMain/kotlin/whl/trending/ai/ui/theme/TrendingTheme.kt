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

    val state = rememberDynamicMaterialThemeState(
        seedColor = Color(seedArgb),
        isDark = isDark,
        style = PaletteStyle.TonalSpot,
    )

    DynamicMaterialTheme(
        state = state,
        animate = true,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
    ) {
        content()
    }
}
