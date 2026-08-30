package whl.trending.chat.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 方言一致性套件：同一批样本在两端解析器（Android=commonmark-java，iOS=cmark-gfm）上
 * 必须产出相同的自有 AST——双解析器方案的行为防线。样本偏向真实 chat 回复的形态，
 * 含流式中间态（未闭合围栏、只有表头的表格）。
 */
@IgnoreIos
class MarkdownDialectTest {

    private fun parse(md: String): List<MdBlock> = parseMarkdown(md).blocks

    private fun text(s: String) = MdInline.Text(s)

    @Test
    fun heading_levels_map_to_ast() {
        val blocks = parse("# 一级\n\n## 二级\n\n#### 四级")
        assertEquals(
            listOf(
                MdBlock.Heading(1, listOf(text("一级"))),
                MdBlock.Heading(2, listOf(text("二级"))),
                MdBlock.Heading(4, listOf(text("四级"))),
            ),
            blocks,
        )
    }

    @Test
    fun paragraph_inline_styles() {
        val blocks = parse("普通 **加粗** *斜体* `code` [链接](https://a.com)")
        assertEquals(
            listOf(
                MdBlock.Paragraph(
                    listOf(
                        text("普通 "),
                        MdInline.Strong(listOf(text("加粗"))),
                        text(" "),
                        MdInline.Emphasis(listOf(text("斜体"))),
                        text(" "),
                        MdInline.Code("code"),
                        text(" "),
                        MdInline.Link("https://a.com", listOf(text("链接"))),
                    ),
                ),
            ),
            blocks,
        )
    }

    @Test
    fun soft_and_hard_breaks() {
        val blocks = parse("第一行\n第二行  \n第三行")
        assertEquals(
            listOf(
                MdBlock.Paragraph(
                    listOf(text("第一行"), MdInline.SoftBreak, text("第二行"), MdInline.HardBreak, text("第三行")),
                ),
            ),
            blocks,
        )
    }

    @Test
    fun nested_lists_with_ordered_start() {
        val blocks = parse("- 甲\n- 乙\n  1. 子一\n  2. 子二\n\n3. 丙")
        val bullet = assertIs<MdBlock.BulletList>(blocks[0])
        assertEquals(2, bullet.items.size)
        assertEquals(listOf<MdBlock>(MdBlock.Paragraph(listOf(text("甲")))), bullet.items[0])
        val nested = assertIs<MdBlock.OrderedList>(bullet.items[1][1])
        assertEquals(1, nested.start)
        assertEquals(2, nested.items.size)
        val ordered = assertIs<MdBlock.OrderedList>(blocks[1])
        assertEquals(3, ordered.start)
    }

    @Test
    fun fenced_code_keeps_language_and_body() {
        val blocks = parse("```kotlin\nval x = 1\nprintln(x)\n```")
        assertEquals(listOf<MdBlock>(MdBlock.CodeBlock("val x = 1\nprintln(x)", "kotlin")), blocks)
    }

    /** 流式中间态：围栏未闭合时，其后内容整体按代码块渲染（两端必须一致，否则流式期间跨端观感分叉）。 */
    @Test
    fun unclosed_fence_is_treated_as_code() {
        val blocks = parse("前文\n\n```python\nimport os")
        assertEquals(
            listOf(
                MdBlock.Paragraph(listOf(text("前文"))),
                MdBlock.CodeBlock("import os", "python"),
            ),
            blocks,
        )
    }

    @Test
    fun indented_code_block_has_no_language() {
        val blocks = parse("段落：\n\n    ls -la\n    pwd")
        assertEquals(
            listOf(
                MdBlock.Paragraph(listOf(text("段落："))),
                MdBlock.CodeBlock("ls -la\npwd", ""),
            ),
            blocks,
        )
    }

    @Test
    fun blockquote_and_thematic_break() {
        val blocks = parse("> 引用一行\n\n---")
        assertEquals(
            listOf(
                MdBlock.Quote(listOf(MdBlock.Paragraph(listOf(text("引用一行"))))),
                MdBlock.ThematicBreak,
            ),
            blocks,
        )
    }

    @Test
    fun table_with_alignments() {
        val blocks = parse(
            """
            | 名称 | 数量 | 价格 |
            |:-----|:----:|-----:|
            | 甲   | 2    | 3.5  |
            | 乙   | 4    | 7.0  |
            """.trimIndent(),
        )
        val table = assertIs<MdBlock.Table>(blocks.single())
        assertEquals(
            listOf(MdAlign.START, MdAlign.CENTER, MdAlign.END),
            table.header.map { it.alignment },
        )
        assertEquals(listOf<MdInline>(text("名称")), table.header[0].inline)
        assertEquals(2, table.rows.size)
        assertEquals(listOf<MdInline>(text("7.0")), table.rows[1][2].inline)
    }

    /** 流式中间态：表头 + 分隔行刚出、body 未到——应已是一张 0 行表格。 */
    @Test
    fun table_header_only_mid_state() {
        val table = assertIs<MdBlock.Table>(parse("| A | B |\n|---|---|").single())
        assertEquals(2, table.header.size)
        assertTrue(table.rows.isEmpty())
    }

    @Test
    fun images_absolute_and_relative() {
        val blocks = parse("![截图](https://a.com/x.png) 与 ![本地](docs/y.png)")
        val inline = assertIs<MdBlock.Paragraph>(blocks.single()).inline
        assertEquals(MdInline.Image("https://a.com/x.png", listOf(text("截图"))), inline[0])
        assertEquals(MdInline.Image("docs/y.png", listOf(text("本地"))), inline[2])
    }

    @Test
    fun raw_html_is_dropped_not_rendered() {
        val blocks = parse("前 <br> 后")
        val inline = assertIs<MdBlock.Paragraph>(blocks.single()).inline
        // HtmlInline 丢弃，文本保留
        assertEquals(listOf<MdInline>(text("前 "), text(" 后")), inline)
    }

    @Test
    fun heading_without_blank_line_after_paragraph() {
        // AI 输出常见：段落后紧跟标题行（无空行）
        val blocks = parse("总结如下\n## 要点")
        assertEquals(
            listOf(
                MdBlock.Paragraph(listOf(text("总结如下"))),
                MdBlock.Heading(2, listOf(text("要点"))),
            ),
            blocks,
        )
    }
}
