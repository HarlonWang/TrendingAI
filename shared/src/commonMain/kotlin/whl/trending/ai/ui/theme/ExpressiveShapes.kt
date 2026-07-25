package whl.trending.ai.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon

/**
 * M3 Expressive 形状工具。
 *
 * [MaterialShapes] 给的是 [RoundedPolygon]（Cookie / Sunny / Clover…）而不是 Compose 的 [Shape]，
 * 要喂给 Surface 得自己包一层。material3 自带 `RoundedPolygon.toShape()`，但只支持单个多边形；
 * 这里补的是它没有的那半边——两个多边形之间的形变（morph），用来把「选中」表达成形状变化
 * 而不是加一圈描边。
 */

/**
 * 把 [Morph] 在 [progress]（0f..1f）处的形状裁成 [Shape]。
 *
 * 缩放照搬 material3 `toShape()` 的做法：[MaterialShapes] 的多边形都是归一化的（坐标 0..1），
 * 直接按布局尺寸整体缩放，再按各自 bounds 的中心对齐——非正方形的中间态才不会偏到一边，
 * 卡片里的勾也就始终在正中。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
class MorphPolygonShape(
    private val morph: Morph,
    private val progress: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val path: Path = morph.toPath(progress = progress)
        path.transform(Matrix().apply { scale(x = size.width, y = size.height) })
        path.translate(size.center - path.getBounds().center)
        return Outline.Generic(path)
    }
}

/** 记住两个多边形之间的 [Morph]，避免每帧重建 */
@Composable
fun rememberMorph(start: RoundedPolygon, end: RoundedPolygon): Morph =
    remember(start, end) { Morph(start, end) }

/**
 * 主题色卡用的一对形状：常态是 9 瓣饼干，选中时形变成太阳形。
 * 两者瓣数接近、轮廓差异又足够明显，形变过程平滑且看得出变化。
 */
object SwatchShapes {
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    val idle: RoundedPolygon get() = MaterialShapes.Cookie9Sided

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    val selected: RoundedPolygon get() = MaterialShapes.Sunny
}
