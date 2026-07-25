package whl.trending.ai.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.back
import trendingai.shared.generated.resources.color_lab_contrast
import trendingai.shared.generated.resources.color_lab_hex
import trendingai.shared.generated.resources.color_lab_hex_invalid
import trendingai.shared.generated.resources.color_lab_hue
import trendingai.shared.generated.resources.color_lab_preview_action
import trendingai.shared.generated.resources.color_lab_preview_chip
import trendingai.shared.generated.resources.color_lab_preview_desc
import trendingai.shared.generated.resources.color_lab_preview_item
import trendingai.shared.generated.resources.color_lab_preview_title
import trendingai.shared.generated.resources.color_lab_reset
import trendingai.shared.generated.resources.color_lab_saturation_value
import trendingai.shared.generated.resources.color_lab_style
import trendingai.shared.generated.resources.color_lab_title
import whl.trending.ai.core.platform.trackEvent
import whl.trending.ai.data.local.DEFAULT_SEED_ARGB
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.ui.theme.Hsv
import whl.trending.ai.ui.theme.ThemeContrastOption
import whl.trending.ai.ui.theme.ThemeStyleOption
import whl.trending.ai.ui.theme.argbToHsv
import whl.trending.ai.ui.theme.formatHexColor
import whl.trending.ai.ui.theme.hsvToArgb
import whl.trending.ai.ui.theme.parseHexColor
import whl.trending.ai.ui.theme.resolveThemeConfig

/** 拖动取色时的落盘防抖：拖一次会产生几十个中间值，全写盘既费 IO 也无意义 */
private const val PERSIST_DEBOUNCE_MS = 120L

/**
 * 调色台：自定义主题色的二级页。
 *
 * 进入时以「当前生效的那一档」为起点预填（选中蓝档就是蓝色 + 鲜艳），
 * 改动任意一项即落成自定义档，预设本身永不被改写——用户随时点回预设就能恢复。
 *
 * 页面本身没有「应用」按钮：所有改动实时写进 settings，整个 App 立刻跟着变色，
 * 这一页看到的预览卡只是补充（设置页的彩色暴露面太少，光看这页容易判断失真）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorLabScreen(onBack: () -> Unit) {
    // 只在进入时读一次：之后以本页的本地 state 为准，避免自己写入 settings 又被回灌造成抖动
    val initial = remember {
        resolveThemeConfig(
            seedArgb = globalSettingsManager.currentSeedColor(),
            isCustom = globalSettingsManager.currentThemeCustom(),
            styleStorage = globalSettingsManager.currentThemeStyle(),
            contrastStorage = globalSettingsManager.currentThemeContrast(),
        )
    }

    var hsv by remember { mutableStateOf(argbToHsv(initial.seedArgb)) }
    var style by remember { mutableStateOf(initial.style) }
    var contrast by remember { mutableStateOf(initial.contrast) }
    var hexText by remember { mutableStateOf(formatHexColor(initial.seedArgb)) }
    var hexError by remember { mutableStateOf(false) }

    // 落盘节拍器：每次用户交互 +1，触发一次防抖写入；0 表示「没有待写的改动」。
    // 用它而不是直接监听 (色值, 风格, 对比度)，是为了守住两条语义：
    //   1. 只进来看看、什么都没动 → 不该被写成自定义档；
    //   2. 点「恢复默认」后把它清零 → 连带取消挂起的写，否则恢复完又被写回自定义。
    var pendingWrite by remember { mutableStateOf(0) }

    LaunchedEffect(pendingWrite) {
        if (pendingWrite == 0) return@LaunchedEffect
        delay(PERSIST_DEBOUNCE_MS)
        globalSettingsManager.setCustomTheme(
            hsvToArgb(hsv),
            style.storageValue,
            contrast.storageValue,
        )
    }

    // 埋点只在离开时上报一次最终配置：拖动过程中的中间值没有分析价值。
    // 只看看没改的用户不上报，否则「用过调色台」的量会被逛一圈的人稀释。
    // 不记具体色值——那是用户的个人偏好，聚合后也说明不了什么。
    val finalStyle by rememberUpdatedState(style)
    val finalContrast by rememberUpdatedState(contrast)
    val touched by rememberUpdatedState(pendingWrite > 0)
    DisposableEffect(Unit) {
        onDispose {
            if (!touched) return@onDispose
            trackEvent(
                "settings_custom_theme",
                mapOf(
                    "style" to finalStyle.storageValue,
                    "contrast" to finalContrast.storageValue,
                ),
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.color_lab_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            PreviewCard()

            Spacer(Modifier.height(24.dp))

            SectionLabel(stringResource(Res.string.color_lab_saturation_value))
            SaturationValuePanel(
                hsv = hsv,
                onChange = {
                    hsv = it
                    hexText = formatHexColor(hsvToArgb(it))
                    hexError = false
                    pendingWrite++
                },
            )

            Spacer(Modifier.height(16.dp))

            SectionLabel(stringResource(Res.string.color_lab_hue))
            HueSlider(
                hue = hsv.hue,
                onChange = {
                    hsv = hsv.copy(hue = it)
                    hexText = formatHexColor(hsvToArgb(hsv))
                    hexError = false
                    pendingWrite++
                },
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = hexText,
                onValueChange = { raw ->
                    // 只留十六进制字符并截断到 6 位，用户粘贴带 # 或多余字符也能用
                    val cleaned = raw.trim().removePrefix("#")
                        .filter { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
                        .take(6)
                        .uppercase()
                    hexText = cleaned
                    val parsed = parseHexColor(cleaned)
                    if (parsed != null) {
                        hsv = argbToHsv(parsed)
                        hexError = false
                        pendingWrite++
                    } else {
                        // 输到一半（不足 6 位）不算错，只有非空且解析不出来才提示
                        hexError = cleaned.isNotEmpty()
                    }
                },
                label = { Text(stringResource(Res.string.color_lab_hex)) },
                prefix = { Text("#") },
                singleLine = true,
                isError = hexError,
                supportingText = if (hexError) {
                    { Text(stringResource(Res.string.color_lab_hex_invalid)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(24.dp))

            SectionLabel(stringResource(Res.string.color_lab_style))
            StyleChips(
                selected = style,
                onSelect = {
                    style = it
                    pendingWrite++
                },
            )

            Spacer(Modifier.height(24.dp))

            SectionLabel(stringResource(Res.string.color_lab_contrast))
            ContrastRow(
                selected = contrast,
                onSelect = {
                    contrast = it
                    pendingWrite++
                },
            )

            Spacer(Modifier.height(24.dp))

            TextButton(
                onClick = {
                    // 恢复默认 = 回到「默认紫」预设档，并抹掉自定义色（外观页那颗自定义圆随之消失）
                    globalSettingsManager.clearCustomTheme()
                    val restored = resolveThemeConfig(DEFAULT_SEED_ARGB, isCustom = false)
                    hsv = argbToHsv(DEFAULT_SEED_ARGB)
                    style = restored.style
                    contrast = restored.contrast
                    hexText = formatHexColor(DEFAULT_SEED_ARGB)
                    hexError = false
                    // 清零以取消挂起的防抖写，否则刚恢复的默认档又会被写回自定义
                    pendingWrite = 0
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(stringResource(Res.string.color_lab_reset))
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

/**
 * 预览卡：把 primary / secondaryContainer / surface 三层同时摆出来。
 *
 * 存在的理由是设置页本身几乎全是中性色，只看这一页会误判"换了颜色没反应"——
 * 实测中就踩过这个坑（TonalSpot 档在首页几乎看不出色规差异）。
 */
@Composable
private fun PreviewCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(Res.string.color_lab_preview_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(Res.string.color_lab_preview_item),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(Res.string.color_lab_preview_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(12.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(onClick = {}) {
                    Text(stringResource(Res.string.color_lab_preview_action))
                }
                AssistChip(
                    onClick = {},
                    label = { Text(stringResource(Res.string.color_lab_preview_chip)) },
                )
            }
        }
    }
}

/**
 * 饱和度（横轴）× 明度（纵轴）取色面板。
 *
 * 底色是当前色相的纯色，叠加「白→透明」横向渐变与「透明→黑」纵向渐变，
 * 这是 HSV 面板的标准画法：任意一点的颜色恰好等于 Hsv(hue, x/w, 1 - y/h)。
 */
@Composable
private fun SaturationValuePanel(
    hsv: Hsv,
    onChange: (Hsv) -> Unit,
) {
    val pureHue = Color(hsvToArgb(Hsv(hsv.hue, 1f, 1f)))
    val description = stringResource(Res.string.color_lab_saturation_value)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(16.dp))
            .semantics { contentDescription = description }
            .pointerInput(hsv.hue) {
                detectTapGestures { offset ->
                    onChange(offsetToSv(hsv.hue, offset, size.width.toFloat(), size.height.toFloat()))
                }
            }
            .pointerInput(hsv.hue) {
                detectDragGestures { change, _ ->
                    change.consume()
                    onChange(
                        offsetToSv(
                            hsv.hue,
                            change.position,
                            size.width.toFloat(),
                            size.height.toFloat(),
                        ),
                    )
                }
            },
    ) {
        drawRect(color = pureHue)
        drawRect(brush = Brush.horizontalGradient(listOf(Color.White, Color.Transparent)))
        drawRect(brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        drawThumb(
            center = Offset(
                x = hsv.saturation.coerceIn(0f, 1f) * size.width,
                y = (1f - hsv.value.coerceIn(0f, 1f)) * size.height,
            ),
        )
    }
}

/** 色相条：0..360 的完整色轮铺成一条横向渐变 */
@Composable
private fun HueSlider(
    hue: Float,
    onChange: (Float) -> Unit,
) {
    val hueColors = remember {
        (0..360 step 60).map { Color(hsvToArgb(Hsv(it.toFloat(), 1f, 1f))) }
    }
    val description = stringResource(Res.string.color_lab_hue)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onChange((offset.x / size.width.toFloat()).coerceIn(0f, 1f) * 360f)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    onChange((change.position.x / size.width.toFloat()).coerceIn(0f, 1f) * 360f)
                }
            },
    ) {
        drawRect(brush = Brush.horizontalGradient(hueColors))
        drawThumb(
            center = Offset(
                x = (hue / 360f).coerceIn(0f, 1f) * size.width,
                y = size.height / 2f,
            ),
        )
    }
}

/** 取色光标：白圈 + 黑描边，保证在任何底色上都看得见 */
private fun DrawScope.drawThumb(center: Offset) {
    drawCircle(color = Color.White, radius = 10f, center = center)
    drawCircle(color = Color.Black.copy(alpha = 0.4f), radius = 12f, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
}

/** 面板坐标 → HSV；越界的拖拽（手指滑出面板）钳制到边缘而非丢弃 */
private fun offsetToSv(hue: Float, offset: Offset, width: Float, height: Float): Hsv = Hsv(
    hue = hue,
    saturation = (offset.x / width).coerceIn(0f, 1f),
    value = (1f - offset.y / height).coerceIn(0f, 1f),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StyleChips(
    selected: ThemeStyleOption,
    onSelect: (ThemeStyleOption) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ThemeStyleOption.entries.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(stringResource(option.labelRes)) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContrastRow(
    selected: ThemeContrastOption,
    onSelect: (ThemeContrastOption) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        ThemeContrastOption.entries.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = ThemeContrastOption.entries.size,
                ),
                label = {
                    Text(
                        text = stringResource(option.labelRes),
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
            )
        }
    }
}
