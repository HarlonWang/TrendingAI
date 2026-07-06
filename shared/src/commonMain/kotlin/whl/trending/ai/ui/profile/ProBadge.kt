package whl.trending.ai.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.profile_pro_badge
import trendingai.shared.generated.resources.profile_pro_badge_desc

// 金色实心徽章：自成一体，深/浅主题下对比度都稳（不依赖背景色）
private val ProGold = Color(0xFFFFC107)   // amber 底
private val OnProGold = Color(0xFF3D2C00) // 深棕，压在金底上保证可读

/**
 * Pro 荣誉徽章：金色胶囊 + 皇冠图标 + "PRO" 文字，行内跟在个人主页名字右侧。
 * 仅在用户有生效 Pro 权益时展示（由调用方判断 isPro）。
 */
@Composable
fun ProBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(ProGold)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            imageVector = CrownIcon,
            contentDescription = stringResource(Res.string.profile_pro_badge_desc),
            tint = OnProGold,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = stringResource(Res.string.profile_pro_badge),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            color = OnProGold,
        )
    }
}

/**
 * 手绘皇冠矢量（Material 无原生皇冠），三尖两谷 + 底座，颜色由 tint 控制。
 * 参照 ui/home/BrandIcons.kt 的 ImageVector.Builder 模式。
 */
val CrownIcon: ImageVector by lazy {
    ImageVector.Builder(
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            // 从左尖顶起，顺时针：左尖 → 谷 → 中尖 → 谷 → 右尖 → 底座右 → 底座左 → 闭合
            moveTo(2.5f, 7f)
            lineTo(7f, 12f)
            lineTo(12f, 5.5f)
            lineTo(17f, 12f)
            lineTo(21.5f, 7f)
            lineTo(20f, 18.5f)
            lineTo(4f, 18.5f)
            close()
        }
    }.build()
}
