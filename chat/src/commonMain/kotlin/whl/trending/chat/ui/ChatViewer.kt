package whl.trending.chat.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import whl.trending.chat.model.ChatMessage
import whl.trending.chat.model.Role

/** 压在聊天页之上的全屏查看器。文字放大只记消息 id，内容每帧从消息列表取，流式中打开会跟着长。 */
sealed interface ChatViewer {
    data class Text(val messageId: Long) : ChatViewer
    data class Image(val model: Any) : ChatViewer
}

@Composable
internal fun ChatViewerOverlay(
    viewer: ChatViewer?,
    messages: List<ChatMessage>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = viewer != null,
        onBackCompleted = onDismiss,
    )
    // 退场动画期间 viewer 已置空，画面要沿用最后一次的内容
    var shown by remember { mutableStateOf(viewer) }
    if (viewer != null) shown = viewer
    AnimatedVisibility(visible = viewer != null, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        when (val current = shown) {
            is ChatViewer.Text -> {
                val message = messages.firstOrNull { it.id == current.messageId }
                LaunchedEffect(message == null) { if (message == null) onDismiss() }
                if (message != null) {
                    TextViewer(
                        content = message.content,
                        markdown = message.role == Role.ASSISTANT,
                        onDismiss = onDismiss,
                    )
                }
            }
            is ChatViewer.Image -> ImageViewer(model = current.model, onDismiss = onDismiss)
            null -> Unit
        }
    }
}
