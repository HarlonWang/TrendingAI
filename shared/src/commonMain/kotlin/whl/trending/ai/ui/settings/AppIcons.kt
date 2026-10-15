package whl.trending.ai.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.app_icon_cream
import trendingai.shared.generated.resources.ic_app_glyph
import wang.harlon.appsettings.appearance.AppIconOption
import wang.harlon.appsettings.appearance.DefaultPalette

/**
 * 动态图标档位。alias 在 androidApp 的 manifest 里声明，图标资源在 res；切换机制、真实状态读取与选择 UI 在 appsettings-kmp。
 * [backgroundArgb] 是外观页预览底色，**必须与 androidApp `res/values/ic_launcher_variants.xml` 同名色值一致**（两处手动同步）。
 * iOS 没有备用图标，`iosIconName` 全为 null，库会按「不支持」隐藏这一节。
 */
private class IconVariant(
    val id: String,
    val alias: String,
    val backgroundArgb: Long,
    val darkGlyph: Boolean = false,
    val label: @Composable () -> String,
)

private fun paletteLabel(id: String): @Composable () -> String = DefaultPalette.first { it.id == id }.label

private val iconVariants = listOf(
    IconVariant("default", "whl.trending.ai.MainActivityDefault", 0xFF6750A4, label = paletteLabel("default")),
    IconVariant("graphite", "whl.trending.ai.MainActivityGraphite", 0xFF46464A, label = paletteLabel("graphite")),
    IconVariant("steel", "whl.trending.ai.MainActivitySteel", 0xFF4A5C92, label = paletteLabel("steel")),
    IconVariant("pine", "whl.trending.ai.MainActivityPine", 0xFF206A5D, label = paletteLabel("pine")),
    IconVariant("berry", "whl.trending.ai.MainActivityBerry", 0xFFA03B49, label = paletteLabel("berry")),
    // 浅底上白箭头不可见，预览与真实图标都换深色 glyph
    IconVariant("cream", "whl.trending.ai.MainActivityCream", 0xFFF1E7D8, darkGlyph = true, label = { stringResource(Res.string.app_icon_cream) }),
)

/** 交给 `rememberAppSettings { appearance { appIcons = trendingAppIcons } }`。 */
val trendingAppIcons: List<AppIconOption> = iconVariants.map { variant ->
    AppIconOption(
        id = variant.id,
        androidAlias = variant.alias,
        iosIconName = null,
        label = variant.label,
        preview = { GlyphPreview(variant) },
    )
}

/** 「底色圆 + 箭头 glyph」实时合成，与真实图标同构、零位图资源。 */
@Composable
private fun GlyphPreview(variant: IconVariant) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(variant.backgroundArgb)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(Res.drawable.ic_app_glyph),
            contentDescription = null,
            // glyph 底稿是白色，浅底档 tint 成深色，与真实图标的深色 foreground 一致
            colorFilter = ColorFilter.tint(if (variant.darkGlyph) Color(0xFF4C4234) else Color.White),
            modifier = Modifier.fillMaxSize(0.55f),
        )
    }
}
