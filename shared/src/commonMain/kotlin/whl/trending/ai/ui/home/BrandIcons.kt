package whl.trending.ai.ui.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import trendingai.shared.generated.resources.GitHub_Invertocat_Black
import trendingai.shared.generated.resources.GitHub_Invertocat_White
import trendingai.shared.generated.resources.Res

val HackerNewsOrange = Color(0xFFFF6600)

/** GitHub Invertocat 按当前主题深浅选黑/白版；首页顶栏与设置捐助弹窗共用，避免各处复制判定。 */
@Composable
fun githubLogoPainter(): Painter {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return painterResource(
        if (isDark) Res.drawable.GitHub_Invertocat_White else Res.drawable.GitHub_Invertocat_Black
    )
}

/**
 * 纯 Y 字母图标，用于底部导航栏（颜色由 tint 控制）
 */
val HackerNewsYIcon: ImageVector by lazy {
    ImageVector.Builder(
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 64f,
        viewportHeight = 64f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            // 加粗 Y
            moveTo(36.3f, 37.5f)
            verticalLineTo(54f)
            horizontalLineTo(27.7f)
            verticalLineTo(37.5f)
            lineTo(12.5f, 8f)
            horizontalLineTo(21.1f)
            lineTo(32f, 28.5f)
            lineTo(42.9f, 8f)
            horizontalLineTo(51.5f)
            close()
        }
    }.build()
}

/**
 * 纯 P 字母图标，用于底部导航栏（颜色由 tint 控制）
 * 粗细与 HackerNewsYIcon 保持一致
 */
val ProductHuntPIcon: ImageVector by lazy {
    ImageVector.Builder(
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 64f,
        viewportHeight = 64f
    ).apply {
        path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
            // 竖杆
            moveTo(16f, 6f)
            horizontalLineTo(25f)
            verticalLineTo(56f)
            horizontalLineTo(16f)
            close()
            // P 的头部外轮廓（两段弧线）
            moveTo(25f, 6f)
            horizontalLineTo(34f)
            curveTo(44f, 6f, 50f, 11f, 50f, 22f)
            curveTo(50f, 33f, 44f, 38f, 34f, 38f)
            horizontalLineTo(25f)
            close()
            // P 的头部内镂空（两段弧线，保持笔画宽度 ~9）
            moveTo(25f, 15f)
            horizontalLineTo(33f)
            curveTo(37f, 15f, 41f, 17f, 41f, 22f)
            curveTo(41f, 27f, 37f, 29f, 33f, 29f)
            horizontalLineTo(25f)
            close()
        }
    }.build()
}

/**
 * 带方形背景的 HN 图标，用于 TopBar
 */
fun hackerNewsIcon(bgColor: Color): ImageVector {
    // 原始 Y 字母在 64x64 视口中，四周间距不足
    // 将方形扩大到 76x76 并居中 Y，既增加 Y 与边缘的间距，又通过外边距 3 控制整体大小
    val outerPadding = 3f    // 图标与 viewBox 边缘的间距
    val innerPadding = 10f   // Y 字母与方形边缘的额外间距
    val boxSize = 64f + innerPadding * 2  // 方形大小 76
    val viewportSize = boxSize + outerPadding * 2  // 视口大小 82
    val yOffset = outerPadding + innerPadding  // Y 字母偏移 9

    return ImageVector.Builder(
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = viewportSize,
        viewportHeight = viewportSize
    ).apply {
        path(fill = SolidColor(bgColor)) {
            moveTo(outerPadding, outerPadding)
            lineTo(outerPadding + boxSize, outerPadding)
            lineTo(outerPadding + boxSize, outerPadding + boxSize)
            lineTo(outerPadding, outerPadding + boxSize)
            close()
        }
        path(fill = SolidColor(Color.White)) {
            // 加粗 Y：竖杆宽 11，左右笔画宽 11
            moveTo(37.5f + yOffset, 38f + yOffset)
            verticalLineTo(54f + yOffset)
            horizontalLineTo(26.5f + yOffset)
            verticalLineTo(38f + yOffset)
            lineTo(10f + yOffset, 8f + yOffset)
            horizontalLineTo(21f + yOffset)
            lineTo(32f + yOffset, 27f + yOffset)
            lineTo(43f + yOffset, 8f + yOffset)
            horizontalLineTo(54f + yOffset)
            close()
        }
    }.build()
}
