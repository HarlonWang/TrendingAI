package whl.trending.ai.ui.home

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.unit.dp

/**
 * Material Symbols 的 kid_star（圆角星）实心/描边两态，用于底栏 Picks 项。
 *
 * Compose 自带的经典 Material Icons 集没有这个图标（只有 Star / StarOutline 那种尖角星），
 * 所以按 [BrandIcons] 的做法自绘：path 原样取自 google/material-design-icons 仓库的
 * `symbols/web/kid_star/materialsymbolsoutlined` 24px 版本，颜色交给 `Icon` 的 tint。
 *
 * Symbols 的 viewBox 是 `0 -960 960 960`——y 落在 -960..0，直接喂给 ImageVector 会整个跑到
 * 画布外，因此把路径包进一个 `translationY = 960` 的 group 搬回 0..960。
 */
private const val KID_STAR_FILLED_PATH =
    "m305-704 112-145q12-16 28.5-23.5T480-880q18 0 34.5 7.5T543-849l112 145 170 57q26 8 41 " +
        "29.5t15 47.5q0 12-3.5 24T866-523L756-367l4 164q1 35-23 59t-56 24q-2 0-22-3l-179-50-179 " +
        "50q-5 2-11 2.5t-11 .5q-32 0-56-24t-23-59l4-165L95-523q-8-11-11.5-23T80-570q0-25 " +
        "14.5-46.5T135-647l170-57Z"

private const val KID_STAR_OUTLINED_PATH =
    "m305-704 112-145q12-16 28.5-23.5T480-880q18 0 34.5 7.5T543-849l112 145 170 57q26 8 41 " +
        "29.5t15 47.5q0 12-3.5 24T866-523L756-367l4 164q1 35-23 59t-56 24q-2 0-22-3l-179-50-179 " +
        "50q-5 2-11 2.5t-11 .5q-32 0-56-24t-23-59l4-165L95-523q-8-11-11.5-23T80-570q0-25 " +
        "14.5-46.5T135-647l170-57Zm49 69-194 64 124 179-4 191 200-55 200 56-4-192 124-177-194-66-126-165-126 " +
        "165Zm126 135Z"

private fun kidStar(pathData: String): ImageVector = ImageVector.Builder(
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 960f,
    viewportHeight = 960f,
).apply {
    group(translationY = 960f) {
        addPath(pathData = addPathNodes(pathData), fill = SolidColor(Color.Black))
    }
}.build()

/** kid_star 实心态（选中）。 */
val KidStarFilled: ImageVector by lazy { kidStar(KID_STAR_FILLED_PATH) }

/** kid_star 描边态（未选中）。 */
val KidStarOutlined: ImageVector by lazy { kidStar(KID_STAR_OUTLINED_PATH) }
