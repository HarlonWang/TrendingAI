package whl.trending.chat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import whl.trending.chat.R
import whl.trending.chat.markdown.MarkdownText
import whl.trending.chat.model.ChatMessage
import whl.trending.chat.model.MessageStatus
import whl.trending.chat.model.Role

/**
 * 单条消息：
 * - 用户：右侧气泡（primaryContainer）
 * - 助手：左侧全宽 Markdown 渲染（无气泡，贴近 Claude/ChatGPT 风格）
 * 均支持长按选中复制；助手出错时展示错误与重试按钮。
 */
@Composable
fun MessageItem(
    message: ChatMessage,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (message.role) {
        Role.USER -> UserMessage(message, modifier)
        Role.ASSISTANT -> AssistantMessage(message, onRetry, modifier)
    }
}

@Composable
private fun UserMessage(message: ChatMessage, modifier: Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            SelectionContainer {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun AssistantMessage(message: ChatMessage, onRetry: () -> Unit, modifier: Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (message.status == MessageStatus.ERROR) {
            Text(
                text = stringResource(R.string.chat_error_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.chat_retry))
            }
        } else {
            SelectionContainer {
                MarkdownText(
                    markdown = message.content,
                    textStyle = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MessageItemPreview() {
    MaterialTheme {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MessageItem(
                ChatMessage(1, Role.USER, "帮我用 Kotlin 写一个快速排序"),
                onRetry = {},
            )
            MessageItem(
                ChatMessage(
                    2, Role.ASSISTANT,
                    "好的，下面是 **快速排序** 实现：\n\n" +
                        "```kotlin\nfun quickSort(list: List<Int>): List<Int> {\n" +
                        "    if (list.size <= 1) return list\n    val pivot = list.first()\n" +
                        "    val rest = list.drop(1)\n    return quickSort(rest.filter { it < pivot }) +\n" +
                        "        pivot + quickSort(rest.filter { it >= pivot })\n}\n```\n\n" +
                        "- 平均时间复杂度 `O(n log n)`\n- 最坏 `O(n²)`",
                ),
                onRetry = {},
            )
        }
    }
}
