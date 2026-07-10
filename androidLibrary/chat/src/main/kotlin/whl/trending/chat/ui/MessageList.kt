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
import whl.trending.chat.model.ChatMessage
import whl.trending.chat.model.Role

/** 消息列表：LazyColumn，message.id 作稳定 key；新消息 / 思考中 / 流式增量时自动滚到底。 */
@Composable
fun MessageList(
    messages: List<ChatMessage>,
    isSending: Boolean,
    onRetry: (ChatMessage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // 流式渲染中内容在末条 assistant 消息里增长，size 不变——以末条内容长度作额外 key 跟随滚动
    val lastContentLength = messages.lastOrNull()?.content?.length ?: 0
    LaunchedEffect(messages.size, isSending, lastContentLength) {
        val target = messages.size // 含末尾 typing 项时仍滚到底
        if (target > 0) listState.scrollToItem(target)
    }

    // 流式占位一旦开始出字（或出错）就收起 typing 指示器，避免「正文下面还转圈」
    val last = messages.lastOrNull()
    val showTyping = isSending &&
        (last == null || last.role == Role.USER || (last.content.isBlank() && last.error == null))

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(messages, key = { it.id }) { message ->
            MessageItem(message = message, onRetry = { onRetry(message) })
        }
        if (showTyping) {
            item(key = "typing") { TypingIndicator() }
        }
    }
}
