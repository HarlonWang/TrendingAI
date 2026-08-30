package whl.trending.chat.markdown

import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableBody
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
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
import org.commonmark.parser.Parser

/** 线程安全、可复用的单例 Parser，启用 GFM 表格扩展（ChatGPT 偶尔输出表格）。 */
private val parser: Parser = Parser.builder()
    .extensions(listOf(TablesExtension.create()))
    .build()

actual fun parseMarkdown(markdown: String): MdDocument =
    MdDocument(mapBlocks(parser.parse(markdown)))

private fun Node.children(): List<Node> {
    val result = ArrayList<Node>()
    var child = firstChild
    while (child != null) {
        result.add(child)
        child = child.next
    }
    return result
}

private fun mapBlocks(container: Node): List<MdBlock> =
    container.children().mapNotNull { mapBlock(it) }

private fun mapBlock(node: Node): MdBlock? = when (node) {
    is Heading -> MdBlock.Heading(node.level, mapInlines(node.children()))
    is Paragraph -> MdBlock.Paragraph(mapInlines(node.children()))
    is FencedCodeBlock ->
        MdBlock.CodeBlock(node.literal.orEmpty().trimEnd('\n'), node.info?.trim().orEmpty())
    is IndentedCodeBlock -> MdBlock.CodeBlock(node.literal.orEmpty().trimEnd('\n'), "")
    is BulletList -> MdBlock.BulletList(mapListItems(node))
    is OrderedList -> MdBlock.OrderedList(node.markerStartNumber ?: 1, mapListItems(node))
    is BlockQuote -> MdBlock.Quote(mapBlocks(node))
    is ThematicBreak -> MdBlock.ThematicBreak
    is TableBlock -> mapTable(node)
    else -> {
        // 未覆盖的块（HtmlBlock 等）：与旧渲染兜底一致，能按 inline 走的按段落渲染，否则丢弃
        val inline = mapInlines(node.children())
        if (inline.isEmpty()) null else MdBlock.Paragraph(inline)
    }
}

private fun mapListItems(list: Node): List<List<MdBlock>> =
    list.children().filterIsInstance<ListItem>().map { mapBlocks(it) }

private fun mapTable(table: TableBlock): MdBlock.Table {
    val header = ArrayList<MdTableCell>()
    val rows = ArrayList<List<MdTableCell>>()
    table.children().forEach { section ->
        when (section) {
            is TableHead -> section.children().filterIsInstance<TableRow>().forEach { row ->
                header.addAll(mapCells(row))
            }
            is TableBody -> section.children().filterIsInstance<TableRow>().forEach { row ->
                rows.add(mapCells(row))
            }
        }
    }
    return MdBlock.Table(header, rows)
}

private fun mapCells(row: TableRow): List<MdTableCell> =
    row.children().filterIsInstance<TableCell>().map { cell ->
        val align = when (cell.alignment) {
            TableCell.Alignment.CENTER -> MdAlign.CENTER
            TableCell.Alignment.RIGHT -> MdAlign.END
            else -> MdAlign.START
        }
        MdTableCell(align, mapInlines(cell.children()))
    }

private fun mapInlines(nodes: List<Node>): List<MdInline> {
    val result = ArrayList<MdInline>()
    nodes.forEach { node ->
        when (node) {
            is CmText -> result.add(MdInline.Text(node.literal.orEmpty()))
            is Emphasis -> result.add(MdInline.Emphasis(mapInlines(node.children())))
            is StrongEmphasis -> result.add(MdInline.Strong(mapInlines(node.children())))
            is Code -> result.add(MdInline.Code(node.literal.orEmpty()))
            is Link -> result.add(MdInline.Link(node.destination.orEmpty(), mapInlines(node.children())))
            is Image -> result.add(MdInline.Image(node.destination.orEmpty(), mapInlines(node.children())))
            is SoftLineBreak -> result.add(MdInline.SoftBreak)
            is HardLineBreak -> result.add(MdInline.HardBreak)
            // 未覆盖的 inline（HtmlInline 等）：与旧渲染兜底一致，拍平其子节点
            else -> result.addAll(mapInlines(node.children()))
        }
    }
    return result
}
