package whl.trending.chat.markdown

import org.commonmark.node.Image
import org.commonmark.node.Node
import org.commonmark.node.Text as CmText

/**
 * 段落按图片切分后的渲染片段:文字组按 inline 渲染,图片以块级大图展示。
 */
internal sealed interface InlineSegment {
    /** 连续的非图片 inline 节点(含相对路径等不可加载的图片,按 alt 文本渲染)。 */
    data class Text(val nodes: List<Node>) : InlineSegment

    /** 可块级展示的网络图片。 */
    data class Image(val url: String, val alt: String) : InlineSegment
}

/**
 * 把段落的 inline 子节点切分为「文字组 / 图片」交替序列。
 * 仅 http/https 绝对 URL 的图片提升为 [InlineSegment.Image],其余原样留在文字组。
 */
internal fun splitByImages(paragraph: Node): List<InlineSegment> {
    val segments = ArrayList<InlineSegment>()
    val pending = ArrayList<Node>()
    fun flushText() {
        if (pending.isNotEmpty()) {
            segments.add(InlineSegment.Text(pending.toList()))
            pending.clear()
        }
    }
    var child = paragraph.firstChild
    while (child != null) {
        if (child is Image && child.destination.isHttpUrl()) {
            flushText()
            segments.add(InlineSegment.Image(url = child.destination, alt = child.plainText()))
        } else {
            pending.add(child)
        }
        child = child.next
    }
    flushText()
    return segments
}

private fun String?.isHttpUrl(): Boolean =
    this != null && (startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true))

/** 拍平节点子树中的所有文本字面量(用作图片 alt)。 */
private fun Node.plainText(): String = buildString {
    fun walk(node: Node) {
        if (node is CmText) append(node.literal)
        var child = node.firstChild
        while (child != null) {
            walk(child)
            child = child.next
        }
    }
    walk(this@plainText)
}
