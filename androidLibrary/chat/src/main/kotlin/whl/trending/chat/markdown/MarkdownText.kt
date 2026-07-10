package whl.trending.chat.markdown

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableBody
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text as CmText
import org.commonmark.node.ThematicBreak

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
    val document = remember(markdown) { MarkdownParser.parse(markdown) }
    Column(modifier = modifier) {
        MarkdownBlocks(document.childList(), textStyle, onImageClick)
    }
}

/** 渲染一组块级节点（文档、列表项、引用块的子节点）。 */
@Composable
private fun MarkdownBlocks(nodes: List<Node>, textStyle: TextStyle, onImageClick: (String) -> Unit) {
    nodes.forEachIndexed { index, node ->
        if (index > 0) Spacer(Modifier.padding(top = 4.dp))
        BlockNode(node, textStyle, onImageClick)
    }
}

@Composable
private fun BlockNode(node: Node, textStyle: TextStyle, onImageClick: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val inlineStyles = InlineStyles(
        codeBackground = colors.surfaceVariant,
        linkColor = colors.primary,
    )
    when (node) {
        is Heading -> {
            val style = when (node.level) {
                1 -> MaterialTheme.typography.headlineSmall
                2 -> MaterialTheme.typography.titleLarge
                3 -> MaterialTheme.typography.titleMedium
                else -> MaterialTheme.typography.titleSmall
            }.copy(fontWeight = FontWeight.Bold)
            Text(buildInline(node, inlineStyles), style = style)
        }

        is Paragraph -> {
            val segments = splitByImages(node)
            if (segments.none { it is InlineSegment.Image }) {
                Text(buildInline(node, inlineStyles), style = textStyle)
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

        is FencedCodeBlock -> CodeBlock(code = node.literal.trimEnd('\n'), language = node.info?.trim().orEmpty())

        is IndentedCodeBlock -> CodeBlock(code = node.literal.trimEnd('\n'), language = "")

        is BulletList -> ListBlock(node, textStyle, ordered = false, start = 1, onImageClick = onImageClick)

        is OrderedList ->
            ListBlock(node, textStyle, ordered = true, start = node.markerStartNumber ?: 1, onImageClick = onImageClick)

        is BlockQuote -> Row {
            HorizontalDivider(
                modifier = Modifier.width(3.dp).padding(end = 8.dp),
                color = colors.outline,
            )
            Column { MarkdownBlocks(node.childList(), textStyle, onImageClick) }
        }

        is ThematicBreak -> HorizontalDivider(Modifier.padding(vertical = 4.dp))

        is TableBlock -> MarkdownTable(node, textStyle, inlineStyles)

        else -> {
            // 兜底：未显式处理的块，尝试按 inline 渲染其文本
            val text = buildInline(node, inlineStyles)
            if (text.isNotEmpty()) Text(text, style = textStyle)
        }
    }
}

@Composable
private fun ListBlock(node: Node, textStyle: TextStyle, ordered: Boolean, start: Int, onImageClick: (String) -> Unit) {
    Column {
        var number = start
        node.childList().filterIsInstance<ListItem>().forEach { item ->
            Row {
                val marker = if (ordered) "${number}. " else "•  "
                Text(marker, style = textStyle, fontWeight = if (ordered) FontWeight.Normal else FontWeight.Bold)
                Column { MarkdownBlocks(item.childList(), textStyle, onImageClick) }
            }
            number++
        }
    }
}

@Composable
private fun MarkdownTable(table: TableBlock, textStyle: TextStyle, styles: InlineStyles) {
    val colors = MaterialTheme.colorScheme
    Column {
        table.childList().forEach { section ->
            when (section) {
                is TableHead -> section.childList().filterIsInstance<TableRow>().forEach { row ->
                    TableRowView(row, textStyle.copy(fontWeight = FontWeight.Bold), styles)
                    HorizontalDivider(color = colors.outlineVariant)
                }

                is TableBody -> section.childList().filterIsInstance<TableRow>().forEach { row ->
                    TableRowView(row, textStyle, styles)
                }
            }
        }
    }
}

@Composable
private fun TableRowView(row: TableRow, textStyle: TextStyle, styles: InlineStyles) {
    Row {
        row.childList().filterIsInstance<TableCell>().forEach { cell ->
            Text(
                buildInline(cell, styles),
                style = textStyle,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

/** inline 渲染所需的主题相关样式（在 @Composable 中取色后传入纯函数构建器）。 */
private data class InlineStyles(val codeBackground: Color, val linkColor: Color)

/** 将一个容器节点（Paragraph/Heading/TableCell 等）的 inline 子节点构建为 AnnotatedString。 */
private fun buildInline(container: Node, styles: InlineStyles): AnnotatedString = buildAnnotatedString {
    appendInline(container, styles)
}

/** 将拆段后的 inline 节点组构建为 AnnotatedString（节点仍挂在原 AST 上，仅逐个渲染自身）。 */
private fun buildInline(nodes: List<Node>, styles: InlineStyles): AnnotatedString = buildAnnotatedString {
    nodes.forEach { appendNode(it, styles) }
}

private fun AnnotatedString.Builder.appendInline(node: Node, styles: InlineStyles) {
    var child = node.firstChild
    while (child != null) {
        appendNode(child, styles)
        child = child.next
    }
}

private fun AnnotatedString.Builder.appendNode(child: Node, styles: InlineStyles) {
    when (child) {
        is CmText -> append(child.literal)
        is Emphasis -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { appendInline(child, styles) }
        is StrongEmphasis -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { appendInline(child, styles) }
        is Code -> withStyle(
            SpanStyle(fontFamily = FontFamily.Monospace, background = styles.codeBackground),
        ) { append(child.literal) }

        is Link -> withLink(
            LinkAnnotation.Url(
                url = child.destination,
                styles = TextLinkStyles(
                    SpanStyle(color = styles.linkColor, textDecoration = TextDecoration.Underline),
                ),
            ),
        ) { appendInline(child, styles) }

        // 无法块级展示的图片（标题/表格内、相对路径等）降级为 alt 文本
        is Image -> appendInline(child, styles)

        is SoftLineBreak -> append(" ")
        is HardLineBreak -> append("\n")
        else -> appendInline(child, styles)
    }
}
