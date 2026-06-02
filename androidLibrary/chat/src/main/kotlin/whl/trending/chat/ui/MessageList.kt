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

/** 消息列表：LazyColumn，message.id 作稳定 key；新消息 / 思考中时自动滚到底。 */
@Composable
fun MessageList(
    messages: List<ChatMessage>,
    isSending: Boolean,
    onRetry: (ChatMessage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, isSending) {
        val target = messages.size // 含末尾 typing 项时仍滚到底
        if (target > 0) listState.animateScrollToItem(target)
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(messages, key = { it.id }) { message ->
            MessageItem(message = message, onRetry = { onRetry(message) })
        }
        if (isSending) {
            item(key = "typing") { TypingIndicator() }
        }
    }
}
