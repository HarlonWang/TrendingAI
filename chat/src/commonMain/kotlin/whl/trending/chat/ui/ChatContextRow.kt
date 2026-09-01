package whl.trending.chat.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TonalToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import whl.trending.chat.model.ChatModelsResponse
import trendingai.chat.generated.resources.Res
import trendingai.chat.generated.resources.chat_web_search

/**
 * 输入胶囊正上方的单行「当前配置」区，回答一个问题：**下一条消息会以什么配置发出去**。
 *
 * 参照 EchoFlow 的 `ContextChipRow`（`ChatComposer.kt`）：模型与已开启的能力本就是同一类信息，
 * 排成一行而不是各占一行——改版前它俩各占一行、右侧全是留白，最坏情况能把输入框顶高约 150dp。
 *
 * 行内一律用 M3 Expressive 的按钮而不是 chip。原因是圆角：chip 的规范是 32dp 高 + `CornerSmall`
 * （8dp），下面的输入胶囊是 72dp 高 + 36dp 全圆，两者放一起像方贴纸贴在圆胶囊上。Expressive 的
 * 按钮默认就是 40dp 高 + `CornerFull`，与胶囊同源。（ChatGPT / Gemini / 千问 / 豆包的同位置
 * 控件也都是全圆胶囊，没有用小圆角 chip 的。）
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ChatContextRow(
    catalog: ChatModelsResponse,
    searchActive: Boolean,
    onToggleSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val showModel = chatModelPickerVisible(catalog)
    // 两者都无内容时整行缺席：留一个空 Row 会在胶囊上方多出一段说不清来由的留白
    if (!showModel && !searchActive) return

    Row(
        // 能力将来变多时横向滚动而不是换行：换行会让输入框在开关能力时上下跳
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showModel) ModelPicker(catalog = catalog)
        if (searchActive) {
            // 已开启的能力。用 TonalToggleButton 而不是带 × 的 InputChip：Expressive 的表达方式是
            // 让形状承担状态——选中态是 squircle（CornerMedium），按下时收成 6dp 圆角，撤销那一下
            // 有形变反馈。它只在能力已开启时出现，所以 checked 恒为 true，点击即回到关闭。
            TonalToggleButton(
                checked = true,
                onCheckedChange = { onToggleSearch() },
                // checked 态默认是 secondary 深色实心，摆在这里会变成全屏最重的一块。压到
                // secondaryContainer 后，它成了这一行**唯一带色相**的元素——这是有意的：能力是
                // 临时开启、需要被看见的状态，而模型是常驻信息，退在中性梯度里（见 ModelPicker）。
                colors = ToggleButtonDefaults.tonalToggleButtonColors(
                    checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            ) {
                Icon(
                    imageVector = Icons.Outlined.TravelExplore,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(Res.string.chat_web_search))
            }
        }
    }
}
