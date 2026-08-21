package whl.trending.ai.ui.common

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.beta_badge

/**
 * 标题旁的小号「Beta」徽标，标识该功能仍在实验阶段。
 *
 * 原先只住在 chat 模块里，招聘专题页要用时才上提到这里——chat 是 Android-only 模块且依赖
 * shared，反向引用不到，两处各写一份必然分叉。挂了这个标就意味着做好了下架或大改的准备，
 * 功能稳定后记得摘掉（AI 对话就是这么摘的）。
 */
@Composable
fun BetaBadge(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier,
    ) {
        Text(
            text = stringResource(Res.string.beta_badge),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
