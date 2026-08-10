package whl.trending.ai.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.hackernews_title
import trendingai.shared.generated.resources.producthunt_title

/**
 * Trending 的三源子 tab。文案用各源全称，图标交给顶栏——一行三项不必再塞图标。
 *
 * 样式照搬 Echo 搜索页的内部 tab（`SearchScreen` 的 Explore / Echo Chart / Album）：
 * [SecondaryTabRow] + 透明底，指示器不是整宽下划线，而是一条 32dp 宽、3dp 高、
 * 上缘带圆角的短横条，居中在选中项下方。切内容同样是硬切，Echo 那边也没有转场。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrendingSourceTabs(
    selected: TrendingSource,
    onSelect: (TrendingSource) -> Unit,
) {
    SecondaryTabRow(
        selectedTabIndex = selected.ordinal,
        containerColor = Color.Transparent,
        indicator = {
            Box(
                modifier = Modifier
                    .tabIndicatorOffset(selected.ordinal)
                    .fillMaxWidth(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        },
    ) {
        TrendingSource.entries.forEach { source ->
            val label = when (source) {
                TrendingSource.GitHub -> "GitHub"
                TrendingSource.HackerNews -> stringResource(Res.string.hackernews_title)
                TrendingSource.ProductHunt -> stringResource(Res.string.producthunt_title)
            }
            Tab(
                selected = source == selected,
                onClick = { onSelect(source) },
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                text = { Text(label) },
            )
        }
    }
}
