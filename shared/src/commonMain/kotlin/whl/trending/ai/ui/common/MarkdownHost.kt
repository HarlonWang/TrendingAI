package whl.trending.ai.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Markdown 渲染注入点。
 *
 * 渲染器（commonmark-java + Compose）在 Android-only 的 `androidLibrary/chat` 里，
 * shared 不能直接依赖 JVM 库，故沿用与 [whl.trending.ai.chat.globalChatScreen]、
 * [whl.trending.ai.update.UpdateChecker] 同一套依赖反转：shared 定义插槽，
 * Android 端在 MainActivity 注册实现。未注册（iOS）时由 [MarkdownContent] 兜底。
 */
var globalMarkdownRenderer: (@Composable (markdown: String, modifier: Modifier) -> Unit)? = null

/**
 * 渲染 Markdown 正文。未注册渲染器时退化为纯文本呈现——
 * 源串本身可读（标题带 ###、列表带 -），不至于不可用。
 */
@Composable
fun MarkdownContent(markdown: String, modifier: Modifier = Modifier) {
    val renderer = globalMarkdownRenderer
    if (renderer != null) {
        renderer(markdown, modifier)
    } else {
        Text(
            text = markdown,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = modifier,
        )
    }
}
