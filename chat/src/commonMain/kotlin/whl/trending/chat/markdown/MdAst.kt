package whl.trending.chat.markdown

/**
 * SDK 自有的 Markdown AST：渲染层只认它，解析器是可替换件——
 * Android 映射自 commonmark-java，iOS 映射自 cmark-gfm，两端行为由 commonTest
 * 方言套件（MarkdownDialectTest）锁定。节点集合只覆盖渲染层实际消费的形态。
 */
data class MdDocument(val blocks: List<MdBlock>)

sealed interface MdBlock {
    data class Heading(val level: Int, val inline: List<MdInline>) : MdBlock
    data class Paragraph(val inline: List<MdInline>) : MdBlock

    /** 围栏与缩进代码块统一形态；缩进块 [language] 为空串。literal 不含末尾换行。 */
    data class CodeBlock(val literal: String, val language: String) : MdBlock

    data class BulletList(val items: List<List<MdBlock>>) : MdBlock
    data class OrderedList(val start: Int, val items: List<List<MdBlock>>) : MdBlock
    data class Quote(val blocks: List<MdBlock>) : MdBlock
    data object ThematicBreak : MdBlock
    data class Table(val header: List<MdTableCell>, val rows: List<List<MdTableCell>>) : MdBlock
}

data class MdTableCell(val alignment: MdAlign, val inline: List<MdInline>)

enum class MdAlign { START, CENTER, END }

sealed interface MdInline {
    data class Text(val literal: String) : MdInline
    data class Emphasis(val children: List<MdInline>) : MdInline
    data class Strong(val children: List<MdInline>) : MdInline
    data class Code(val literal: String) : MdInline
    data class Link(val destination: String, val children: List<MdInline>) : MdInline
    data class Image(val destination: String, val children: List<MdInline>) : MdInline
    data object SoftBreak : MdInline
    data object HardBreak : MdInline
}

/**
 * 解析 Markdown（CommonMark + GFM 表格）。解析器线程安全、可重复调用。
 */
expect fun parseMarkdown(markdown: String): MdDocument

/** 拍平 inline 子树的全部文本字面量（图片 alt、纯文本降级用）。 */
fun List<MdInline>.plainText(): String = buildString { appendPlainText(this@plainText) }

private fun StringBuilder.appendPlainText(nodes: List<MdInline>) {
    nodes.forEach { node ->
        when (node) {
            is MdInline.Text -> append(node.literal)
            is MdInline.Code -> append(node.literal)
            is MdInline.Emphasis -> appendPlainText(node.children)
            is MdInline.Strong -> appendPlainText(node.children)
            is MdInline.Link -> appendPlainText(node.children)
            is MdInline.Image -> appendPlainText(node.children)
            MdInline.SoftBreak -> append(" ")
            MdInline.HardBreak -> append("\n")
        }
    }
}
