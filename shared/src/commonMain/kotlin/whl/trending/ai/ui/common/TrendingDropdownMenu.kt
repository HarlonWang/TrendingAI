package whl.trending.ai.ui.common

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 全 app 统一的下拉菜单：固定 24dp 圆角，与卡片组、悬浮胶囊同一套大圆角语言。
 * 别绕开它直接用 DropdownMenu，见 `docs/interaction-consistency-audit.md`。
 */
@Composable
fun TrendingDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MenuDefaults.containerColor,
    tonalElevation: Dp = MenuDefaults.TonalElevation,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = RoundedCornerShape(MenuCorner),
        containerColor = containerColor,
        tonalElevation = tonalElevation,
        content = content,
    )
}

private val MenuCorner = 24.dp
