package whl.trending.chat.markdown

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * 将 Markdown 源串解析并以原生 Compose 渲染。
 *
 * 性能要点：`remember(markdown)` 缓存解析后的 AST，非流式下整段内容终态后只解析一次，
 * 列表滚动重组不会重复解析。
 *
 * @param onImageClick 段落里 http/https 图片被点击时回调（入参为图片 URL），默认无动作
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = LocalTextStyle.current,
    onImageClick: (String) -> Unit = {},
) {
    val document = remember(markdown) { parseMarkdown(markdown) }
    Column(modifier = modifier) {
        MarkdownBlocks(document.blocks, textStyle, onImageClick)
    }
}

/** 渲染一组块级节点（文档、列表项、引用块的子节点）。 */
@Composable
private fun MarkdownBlocks(blocks: List<MdBlock>, textStyle: TextStyle, onImageClick: (String) -> Unit) {
    blocks.forEachIndexed { index, block ->
        if (index > 0) Spacer(Modifier.padding(top = 4.dp))
        BlockNode(block, textStyle, onImageClick)
    }
}

@Composable
private fun BlockNode(block: MdBlock, textStyle: TextStyle, onImageClick: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val inlineStyles = InlineStyles(
        codeBackground = colors.surfaceVariant,
        linkColor = colors.primary,
    )
    when (block) {
        is MdBlock.Heading -> {
            val style = when (block.level) {
                1 -> MaterialTheme.typography.headlineSmall
                2 -> MaterialTheme.typography.titleLarge
                3 -> MaterialTheme.typography.titleMedium
                else -> MaterialTheme.typography.titleSmall
            }.copy(fontWeight = FontWeight.Bold)
            Text(buildInline(block.inline, inlineStyles), style = style)
        }

        is MdBlock.Paragraph -> {
            val segments = splitByImages(block.inline)
            if (segments.none { it is InlineSegment.Image }) {
                Text(buildInline(block.inline, inlineStyles), style = textStyle)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    segments.forEach { segment ->
                        when (segment) {
                            is InlineSegment.Text ->
                                Text(buildInline(segment.nodes, inlineStyles), style = textStyle)
                            is InlineSegment.Image ->
                                MarkdownImage(url = segment.url, alt = segment.alt, onClick = onImageClick)
                        }
                    }
                }
            }
        }

        is MdBlock.CodeBlock -> CodeBlock(code = block.literal, language = block.language)

        is MdBlock.BulletList -> ListBlock(block.items, textStyle, ordered = false, start = 1, onImageClick = onImageClick)

        is MdBlock.OrderedList ->
            ListBlock(block.items, textStyle, ordered = true, start = block.start, onImageClick = onImageClick)

        is MdBlock.Quote -> Row {
            HorizontalDivider(
                modifier = Modifier.width(3.dp).padding(end = 8.dp),
                color = colors.outline,
            )
            Column { MarkdownBlocks(block.blocks, textStyle, onImageClick) }
        }

        is MdBlock.ThematicBreak -> HorizontalDivider(Modifier.padding(vertical = 4.dp))

        is MdBlock.Table -> MarkdownTable(block, textStyle, inlineStyles)
    }
}

@Composable
private fun ListBlock(
    items: List<List<MdBlock>>,
    textStyle: TextStyle,
    ordered: Boolean,
    start: Int,
    onImageClick: (String) -> Unit,
) {
    Column {
        items.forEachIndexed { index, item ->
            Row {
                val marker = if (ordered) "${start + index}. " else "•  "
                Text(marker, style = textStyle, fontWeight = if (ordered) FontWeight.Normal else FontWeight.Bold)
                Column { MarkdownBlocks(item, textStyle, onImageClick) }
            }
        }
    }
}

@Composable
private fun MarkdownTable(table: MdBlock.Table, textStyle: TextStyle, styles: InlineStyles) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Column {
            TableRowView(
                cells = table.header,
                textStyle = textStyle.copy(fontWeight = FontWeight.Bold),
                styles = styles,
                background = colors.surfaceContainerHighest,
            )
            table.rows.forEachIndexed { index, row ->
                // 斑马纹：奇数行加浅底，偶数行透明
                val zebra = if (index % 2 == 1) colors.surfaceContainerLow else Color.Transparent
                TableRowView(row, textStyle, styles, background = zebra)
            }
        }
    }
}

@Composable
private fun TableRowView(cells: List<MdTableCell>, textStyle: TextStyle, styles: InlineStyles, background: Color) {
    Row(Modifier.fillMaxWidth().background(background)) {
        cells.forEach { cell ->
            // GFM 列对齐：解析器已归一为 MdAlign
            val align = when (cell.alignment) {
                MdAlign.CENTER -> TextAlign.Center
                MdAlign.END -> TextAlign.End
                MdAlign.START -> TextAlign.Start
            }
            Text(
                buildInline(cell.inline, styles),
                style = textStyle,
                textAlign = align,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

/** inline 渲染所需的主题相关样式（在 @Composable 中取色后传入纯函数构建器）。 */
private data class InlineStyles(val codeBackground: Color, val linkColor: Color)

private fun buildInline(nodes: List<MdInline>, styles: InlineStyles): AnnotatedString = buildAnnotatedString {
    appendInline(nodes, styles)
}

private fun AnnotatedString.Builder.appendInline(nodes: List<MdInline>, styles: InlineStyles) {
    nodes.forEach { node ->
        when (node) {
            is MdInline.Text -> append(node.literal)
            is MdInline.Emphasis -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                appendInline(node.children, styles)
            }
            is MdInline.Strong -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                appendInline(node.children, styles)
            }
            is MdInline.Code -> withStyle(
                SpanStyle(fontFamily = FontFamily.Monospace, background = styles.codeBackground),
            ) { append(node.literal) }

            is MdInline.Link -> withLink(
                LinkAnnotation.Url(
                    url = node.destination,
                    styles = TextLinkStyles(
                        SpanStyle(color = styles.linkColor, textDecoration = TextDecoration.Underline),
                    ),
                ),
            ) { appendInline(node.children, styles) }

            // 无法块级展示的图片（标题/表格内、相对路径等）降级为 alt 文本
            is MdInline.Image -> appendInline(node.children, styles)

            MdInline.SoftBreak -> append(" ")
            MdInline.HardBreak -> append("\n")
        }
    }
}
