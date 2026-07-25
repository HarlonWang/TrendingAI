package whl.trending.ai.ui.settings

import whl.trending.ai.data.local.DEFAULT_SEED_ARGB
import whl.trending.ai.data.local.ThemeMode
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.core.platform.trackEvent
import whl.trending.ai.ui.theme.Hsv
import whl.trending.ai.ui.theme.MorphPolygonShape
import whl.trending.ai.ui.theme.PRESET_PALETTE
import whl.trending.ai.ui.theme.ThemeSeed
import whl.trending.ai.ui.theme.SwatchShapes
import whl.trending.ai.ui.theme.hsvToArgb
import whl.trending.ai.ui.theme.rememberMorph

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.appearance
import trendingai.shared.generated.resources.back
import trendingai.shared.generated.resources.dark_mode
import trendingai.shared.generated.resources.theme_color
import trendingai.shared.generated.resources.theme_color_custom
import trendingai.shared.generated.resources.theme_dark
import trendingai.shared.generated.resources.theme_follow_system
import trendingai.shared.generated.resources.theme_light

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(
    onBack: () -> Unit,
    onNavigateToColorLab: () -> Unit,
) {
    val themeMode by globalSettingsManager.themeMode.collectAsState(ThemeMode.FOLLOW_SYSTEM)
    val seedColor by globalSettingsManager.seedColor.collectAsState(DEFAULT_SEED_ARGB)
    val isCustom by globalSettingsManager.themeCustom.collectAsState(
        remember { globalSettingsManager.currentThemeCustom() }
    )
    val customSeed by globalSettingsManager.customSeedColor.collectAsState(
        remember { globalSettingsManager.currentCustomSeedColor() }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.appearance)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    Icons.Default.DarkMode,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(Res.string.dark_mode),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = themeMode == mode,
                        onClick = {
                            trackEvent("settings_theme_change", mapOf("theme" to mode.name.lowercase()))
                            globalSettingsManager.setThemeMode(mode)
                        },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = ThemeMode.entries.size
                        ),
                        label = {
                            Text(
                                text = themeModeText(mode),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    Icons.Default.Palette,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(Res.string.theme_color),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
            SwatchGrid(
                selected = seedColor,
                isCustomSelected = isCustom,
                customSeed = customSeed,
                onSelect = { seed ->
                    trackEvent("settings_seed_color", mapOf("seed" to seed.id))
                    globalSettingsManager.setSeedColor(seed.argb)
                },
                onOpenColorLab = {
                    trackEvent("settings_seed_color", mapOf("seed" to "custom"))
                    // 已调过色就先把它应用上，再进调色台——点击只有一种结果，
                    // 不做「选中态下再点才是编辑」那种要靠猜的二段交互
                    globalSettingsManager.selectCustomTheme()
                    onNavigateToColorLab()
                },
            )
        }
    }
}

/**
 * 色卡直径。56dp 而非常见的 40dp：四列撑满通栏后圆若太小会显得松散，
 * 放大既让色相更好辨认，也把触控目标抬到 M3 建议的 48dp 之上。
 */
private val SWATCH_SIZE = 56.dp

/** ThemeMode → 展示文案，设置一级页外观入口与本页分段按钮共用 */
@Composable
internal fun themeModeText(mode: ThemeMode): String {
    val labelRes = when (mode) {
        ThemeMode.FOLLOW_SYSTEM -> Res.string.theme_follow_system
        ThemeMode.LIGHT -> Res.string.theme_light
        ThemeMode.DARK -> Res.string.theme_dark
    }
    return stringResource(labelRes)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SwatchGrid(
    selected: Long,
    isCustomSelected: Boolean,
    customSeed: Long?,
    onSelect: (ThemeSeed) -> Unit,
    onOpenColorLab: () -> Unit,
) {
    // 固定间距左对齐，排不下自然换行——不锁每行颗数，色卡增减时不用跟着调布局。
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PRESET_PALETTE.forEach { seed ->
            val color = Color(seed.argb)
            MorphSwatch(
                name = stringResource(seed.nameRes),
                brush = SolidColor(color),
                contentTint = if (color.luminance() < 0.5f) Color.White else Color.Black,
                idleIcon = null,
                // 自定义色可能恰好等于某个预设的色值，此时预设不该跟着一起显示选中
                selected = !isCustomSelected && seed.argb == selected,
                onClick = { onSelect(seed) },
            )
        }
        CustomSwatch(
            customSeed = customSeed,
            selected = isCustomSelected,
            onClick = onOpenColorLab,
        )
    }
}

/**
 * 色板末尾的自定义档，同时也是调色台入口：入口和结果在同一处，
 * 用户从这里进去、调完回来看到它变成自己的颜色并选中，认知是连贯的。
 * 没调过色时是一圈色轮渐变 + 加号，一眼看出「这颗是随便挑颜色」。
 */
@Composable
private fun CustomSwatch(
    customSeed: Long?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val wheel = remember {
        Brush.sweepGradient(
            (0..360 step 30).map { Color(hsvToArgb(Hsv(it.toFloat(), 0.85f, 0.95f))) }
        )
    }
    val color = customSeed?.let { Color(it) }

    MorphSwatch(
        name = stringResource(Res.string.theme_color_custom),
        brush = color?.let { SolidColor(it) } ?: wheel,
        // 色轮档底色浅深都有，白色勾/加号在任何一段色相上都看得清
        contentTint = if (color == null || color.luminance() < 0.5f) Color.White else Color.Black,
        idleIcon = if (customSeed == null) Icons.Default.Add else null,
        selected = selected,
        onClick = onClick,
    )
}

/**
 * 主题色卡：M3 Expressive 的多边形色卡，选中时形状从 9 瓣饼干形变成太阳形并微微放大，
 * 勾随形变弹入——用形状变化而不是描边来表达选中，和全 app 的 Expressive 取向一致。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MorphSwatch(
    name: String,
    brush: Brush,
    contentTint: Color,
    idleIcon: ImageVector?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val morph = rememberMorph(SwatchShapes.idle, SwatchShapes.selected)
    val progress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
    )
    val swatchScale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(SWATCH_SIZE)
                .scale(swatchScale)
                // shape 依赖 progress，形变过程中每帧都是新实例，这是 morph 的固有代价
                .clip(MorphPolygonShape(morph, progress))
                .background(brush)
                .selectable(
                    selected = selected,
                    role = Role.RadioButton,
                    onClick = onClick,
                )
                .semantics { contentDescription = name },
            contentAlignment = Alignment.Center,
        ) {
            when {
                selected -> Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.scale(progress),
                    tint = contentTint,
                )
                idleIcon != null -> Icon(
                    imageVector = idleIcon,
                    contentDescription = null,
                    tint = contentTint,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
