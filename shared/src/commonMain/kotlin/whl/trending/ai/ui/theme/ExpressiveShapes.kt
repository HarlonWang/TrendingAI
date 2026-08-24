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
 * M3 Expressive 形状工具：补 material3 `RoundedPolygon.toShape()` 没有的那半边——
 * 两个多边形之间的形变（morph），把「选中」表达成形状变化而不是描边。
 */

/**
 * 把 [Morph] 在 [progress]（0f..1f）处的形状裁成 [Shape]。
 * 缩放照搬 material3 `toShape()`：归一化多边形按布局尺寸整体缩放，
 * 再按各自 bounds 中心对齐——非正方形的中间态才不会偏到一边。
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
