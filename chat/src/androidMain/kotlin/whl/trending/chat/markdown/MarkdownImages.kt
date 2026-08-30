package whl.trending.chat.markdown

/**
 * 段落按图片切分后的渲染片段：文字组按 inline 渲染，图片以块级大图展示。
 */
internal sealed interface InlineSegment {
    /** 连续的非图片 inline 节点（含相对路径等不可加载的图片，按 alt 文本渲染）。 */
    data class Text(val nodes: List<MdInline>) : InlineSegment

    /** 可块级展示的网络图片。 */
    data class Image(val url: String, val alt: String) : InlineSegment
}

/**
 * 把段落的 inline 子节点切分为「文字组 / 图片」交替序列。
 * 仅 http/https 绝对 URL 的图片提升为 [InlineSegment.Image]，其余原样留在文字组。
 */
internal fun splitByImages(inline: List<MdInline>): List<InlineSegment> {
    val segments = ArrayList<InlineSegment>()
    val pending = ArrayList<MdInline>()
    fun flushText() {
        if (pending.isNotEmpty()) {
            segments.add(InlineSegment.Text(pending.toList()))
            pending.clear()
        }
    }
    inline.forEach { node ->
        if (node is MdInline.Image && node.destination.isHttpUrl()) {
            flushText()
            segments.add(InlineSegment.Image(url = node.destination, alt = node.children.plainText()))
        } else {
            pending.add(node)
        }
    }
    flushText()
    return segments
}

private fun String.isHttpUrl(): Boolean =
    startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)
