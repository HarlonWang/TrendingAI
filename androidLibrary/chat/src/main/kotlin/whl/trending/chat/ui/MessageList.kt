package whl.trending.chat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import whl.trending.chat.ResearchUpsellPolicy
import whl.trending.chat.model.ChatMessage
import whl.trending.chat.model.Role

/**
 * 消息列表：reverseLayout 的 LazyColumn（经典聊天结构），index 0 = 最新消息，屏幕底部。
 *
 * 流式跟随不用任何程序滚动：贴底（锚点 index 0 / offset 0）时消息内容增长由布局锚点
 * 语义自动保持贴底；用户上滑即离开锚点、跟随自然停止——不存在程序滚动与手势抢夺
 * （此前正向列表 + 每 delta scrollToItem 的方案，会在末项高过一屏后失效并持续杀掉用户 fling）。
 */
@Composable
fun MessageList(
    messages: List<ChatMessage>,
    isSending: Boolean,
    onRetry: (ChatMessage) -> Unit,
    modifier: Modifier = Modifier,
    onResearchUpsell: (() -> Unit)? = null,
) {
    val listState = rememberLazyListState()
    // 「深度调研此项目」升级入口挂载点（发送中不挂，防连点重复扣费）
    val upsellId = if (onResearchUpsell != null && !isSending) {
        ResearchUpsellPolicy.upsellMessageId(messages)
    } else null

    // 仅离散事件（发送/回复到达/typing 项增删）时滚回底部；流式增量期间 size 不变、不触发
    LaunchedEffect(messages.size, isSending) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(0)
    }

    // 流式占位一旦开始出字（或出错）就收起 typing 指示器，避免「正文下面还转圈」
    val last = messages.lastOrNull()
    val showTyping = isSending &&
        (last == null || last.role == Role.USER || (last.content.isBlank() && last.error == null))

    LazyColumn(
        state = listState,
        reverseLayout = true,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // reverseLayout 下内容按「屏幕自下而上」顺序声明：typing 最贴底，随后是最新→最旧消息
        if (showTyping) {
            item(key = "typing") { TypingIndicator() }
        }
        items(messages.asReversed(), key = { it.id }) { message ->
            MessageItem(
                message = message,
                onRetry = { onRetry(message) },
                onResearchUpsell = onResearchUpsell.takeIf { message.id == upsellId },
            )
        }
    }
}
