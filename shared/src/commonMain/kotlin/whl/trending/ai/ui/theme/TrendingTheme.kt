package whl.trending.ai.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.materialkolor.DynamicMaterialTheme
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicMaterialThemeState
import whl.trending.ai.data.local.DEFAULT_SEED_ARGB
import whl.trending.ai.data.local.ThemeMode
import whl.trending.ai.data.local.globalSettingsManager

@Composable
fun TrendingTheme(content: @Composable () -> Unit) {
    val themeMode by globalSettingsManager.themeMode.collectAsState(ThemeMode.FOLLOW_SYSTEM)
    val seedArgb by globalSettingsManager.seedColor.collectAsState(DEFAULT_SEED_ARGB)

    val isDark = when (themeMode) {
        ThemeMode.FOLLOW_SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val state = rememberDynamicMaterialThemeState(
        seedColor = Color(seedArgb.toULong()),
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
