package whl.trending.chat.markdown

import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.Node
import org.commonmark.parser.Parser

/**
 * commonmark-java 解析器封装。线程安全、可复用的单例 Parser，
 * 启用 GFM 表格扩展（ChatGPT 偶尔输出表格）。
 */
object MarkdownParser {

    private val parser: Parser = Parser.builder()
        .extensions(listOf(TablesExtension.create()))
        .build()

    /** 解析 Markdown 源串为 commonmark 文档节点树。 */
    fun parse(markdown: String): Node = parser.parse(markdown)
}

/** 遍历 commonmark 子节点为 List，便于在 Compose 中迭代渲染。 */
internal fun Node.childList(): List<Node> {
    val result = ArrayList<Node>()
    var child = firstChild
    while (child != null) {
        result.add(child)
        child = child.next
    }
    return result
}
