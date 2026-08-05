package whl.trending.ai.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 全 app 统一的底部浮层：固定标题字号、内容边距与底部留白。
 *
 * 之前四处 `ModalBottomSheet` 各写各的，标题字号（`titleLarge` / `titleMedium`）、水平边距
 * （16dp / 24dp）、底部留白（32dp / `navigationBarsPadding` + 16dp）没有任何两处一致，
 * 连续打开筛选面板和登录选择能看出标题一大一小、内容一窄一宽。规格与取舍见
 * `docs/interaction-consistency-audit.md`。
 *
 * 内容区不自带滚动——需要滚动的浮层（如规则说明）在 [content] 里自己挂 `verticalScroll`。
 *
 * @param title 标题，为 null 时不占位（内容自带标题的浮层用这种）
 * @param titleAction 标题右侧的动作位，目前用于「帮助」图标按钮
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendingBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    sheetState: SheetState = rememberModalBottomSheetState(),
    titleAction: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SheetHorizontalPadding)
                .navigationBarsPadding()
                .padding(bottom = SheetBottomPadding),
        ) {
            if (title != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = title, style = MaterialTheme.typography.titleLarge)
                    titleAction?.invoke()
                }
                Spacer(Modifier.height(16.dp))
            }
            content()
        }
    }
}

private val SheetHorizontalPadding = 24.dp
private val SheetBottomPadding = 16.dp
