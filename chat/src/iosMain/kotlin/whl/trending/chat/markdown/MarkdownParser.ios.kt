package whl.trending.chat.markdown

import cmarkgfm.*
import cnames.structs.cmark_node
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.toKString

/**
 * cmark-gfm（apple/swift-cmark 源）映射到自有 AST；与 Android 的 commonmark-java 映射器
 * 行为对齐，由 commonTest 方言套件（MarkdownDialectTest）两端同跑锁定。
 * C 节点树只存活于本函数内：遍历一次构建纯 Kotlin AST 后整树释放，指针不外逸。
 */
@OptIn(ExperimentalForeignApi::class)
actual fun parseMarkdown(markdown: String): MdDocument {
    cmark_gfm_core_extensions_ensure_registered()
    val parser = cmark_parser_new(CMARK_OPT_DEFAULT) ?: return MdDocument(emptyList())
    try {
        cmark_find_syntax_extension("table")?.let { cmark_parser_attach_syntax_extension(parser, it) }
        // 绑定把 const char* 映射为 String?（自动转 UTF-8 C 串）；len 须为 UTF-8 字节数
        cmark_parser_feed(parser, markdown, markdown.encodeToByteArray().size.convert())
        val doc = cmark_parser_finish(parser) ?: return MdDocument(emptyList())
        try {
            return MdDocument(mapBlocks(doc))
        } finally {
            cmark_node_free(doc)
        }
    } finally {
        cmark_parser_free(parser)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun children(node: CPointer<cmark_node>): List<CPointer<cmark_node>> {
    val result = ArrayList<CPointer<cmark_node>>()
    var child = cmark_node_first_child(node)
    while (child != null) {
        result.add(child)
        child = cmark_node_next(child)
    }
    return result
}

@OptIn(ExperimentalForeignApi::class)
private fun literal(node: CPointer<cmark_node>): String =
    cmark_node_get_literal(node)?.toKString().orEmpty()

@OptIn(ExperimentalForeignApi::class)
private fun typeString(node: CPointer<cmark_node>): String =
    cmark_node_get_type_string(node)?.toKString().orEmpty()

@OptIn(ExperimentalForeignApi::class)
private fun mapBlocks(container: CPointer<cmark_node>): List<MdBlock> =
    children(container).mapNotNull { mapBlock(it) }

@OptIn(ExperimentalForeignApi::class)
private fun mapBlock(node: CPointer<cmark_node>): MdBlock? = when (cmark_node_get_type(node)) {
    CMARK_NODE_HEADING -> MdBlock.Heading(cmark_node_get_heading_level(node), mapInlines(children(node)))
    CMARK_NODE_PARAGRAPH -> MdBlock.Paragraph(mapInlines(children(node)))
    // cmark 不在类型层区分围栏/缩进；缩进块 fence_info 为空，与 Android 侧形态一致
    CMARK_NODE_CODE_BLOCK -> MdBlock.CodeBlock(
        literal(node).trimEnd('\n'),
        cmark_node_get_fence_info(node)?.toKString()?.trim().orEmpty(),
    )
    CMARK_NODE_LIST -> {
        val items = children(node)
            .filter { cmark_node_get_type(it) == CMARK_NODE_ITEM }
            .map { mapBlocks(it) }
        if (cmark_node_get_list_type(node) == cmark_list_type.CMARK_ORDERED_LIST) {
            MdBlock.OrderedList(cmark_node_get_list_start(node).takeIf { it > 0 } ?: 1, items)
        } else {
            MdBlock.BulletList(items)
        }
    }
    CMARK_NODE_BLOCK_QUOTE -> MdBlock.Quote(mapBlocks(node))
    CMARK_NODE_THEMATIC_BREAK -> MdBlock.ThematicBreak
    else -> when (typeString(node)) {
        "table" -> mapTable(node)
        else -> {
            // 未覆盖的块（html_block 等）：与 Android 映射器兜底一致
            val inline = mapInlines(children(node))
            if (inline.isEmpty()) null else MdBlock.Paragraph(inline)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun mapTable(table: CPointer<cmark_node>): MdBlock.Table {
    val columns = cmark_gfm_extensions_get_table_columns(table).toInt()
    val alignments = cmark_gfm_extensions_get_table_alignments(table)
    fun alignAt(index: Int): MdAlign = when (alignments?.get(index)?.toInt()?.toChar()) {
        'c' -> MdAlign.CENTER
        'r' -> MdAlign.END
        else -> MdAlign.START
    }

    // 表头行的类型名是 table_header（cmark-gfm 的 AST 形态），body 行是 table_row；
    // GFM spec 保证表头行必在最前，首行即 header
    val allRows = children(table).filter { typeString(it) == "table_header" || typeString(it) == "table_row" }.map { row ->
        children(row).filter { typeString(it) == "table_cell" }
            .mapIndexed { index, cell ->
                MdTableCell(alignAt(index.coerceAtMost(columns - 1)), mapInlines(children(cell)))
            }
    }
    return MdBlock.Table(allRows.firstOrNull().orEmpty(), allRows.drop(1))
}

@OptIn(ExperimentalForeignApi::class)
private fun mapInlines(nodes: List<CPointer<cmark_node>>): List<MdInline> {
    val result = ArrayList<MdInline>()
    nodes.forEach { node ->
        when (cmark_node_get_type(node)) {
            CMARK_NODE_TEXT -> result.add(MdInline.Text(literal(node)))
            CMARK_NODE_EMPH -> result.add(MdInline.Emphasis(mapInlines(children(node))))
            CMARK_NODE_STRONG -> result.add(MdInline.Strong(mapInlines(children(node))))
            CMARK_NODE_CODE -> result.add(MdInline.Code(literal(node)))
            CMARK_NODE_LINK -> result.add(
                MdInline.Link(cmark_node_get_url(node)?.toKString().orEmpty(), mapInlines(children(node))),
            )
            CMARK_NODE_IMAGE -> result.add(
                MdInline.Image(cmark_node_get_url(node)?.toKString().orEmpty(), mapInlines(children(node))),
            )
            CMARK_NODE_SOFTBREAK -> result.add(MdInline.SoftBreak)
            CMARK_NODE_LINEBREAK -> result.add(MdInline.HardBreak)
            // 未覆盖的 inline（html_inline 等）：拍平子节点，与 Android 兜底一致
            else -> result.addAll(mapInlines(children(node)))
        }
    }
    return result
}
