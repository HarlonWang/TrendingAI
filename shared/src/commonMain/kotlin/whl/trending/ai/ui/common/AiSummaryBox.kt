package whl.trending.ai.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * AI 摘要块。全 app 统一用 secondaryContainer 底色标识 AI 生成内容。
 *
 * [media] 用于把配图收进同一个块里——摘要文字在上、图在下，图左右贴到块边、
 * 底部跟随圆角（外层 clip 负责裁）。Product Hunt 列表靠它把"AI 怎么看这个产品"
 * 和"产品长什么样"圈成一个整体。
 */
@Composable
fun AiSummaryBox(
    summary: String,
    modifier: Modifier = Modifier,
    media: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Text(
            text = summary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(12.dp)
        )
        media?.invoke()
    }
}
