package whl.trending.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import whl.trending.chat.markdown.MarkdownText

private const val FONT_SCALE = 1.5f

/**
 * 全屏文字查看器：把一条消息按放大一档的字号铺满整屏，单击任意处退出，长按仍可选择复制。
 * 放大靠覆盖 [LocalDensity] 的 fontScale，Markdown 里标题、代码块与正文按同一比例一起变大。
 *
 * @param markdown 为 true 时按 Markdown 渲染（助手回复），否则按纯文本（用户消息）
 */
@Composable
fun TextViewerDialog(
    content: String,
    markdown: Boolean,
    onDismiss: () -> Unit,
) {
    FullScreenDialog(onDismiss = onDismiss) {
        val density = LocalDensity.current
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) }
                .safeDrawingPadding(),
        ) {
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, density.fontScale * FONT_SCALE),
            ) {
                SelectionContainer {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp, vertical = 20.dp),
                    ) {
                        val style = MaterialTheme.typography.bodyLarge
                        if (markdown) {
                            MarkdownText(markdown = content, textStyle = style)
                        } else {
                            Text(text = content, style = style)
                        }
                    }
                }
            }
        }
    }
}
