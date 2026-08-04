package whl.trending.ai.ui.digest

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DigestMarkdownTest {

    @Test
    fun 标题要求井号加空格_任意级别均渲染为标题() {
        val blocks = parseBlocks("### 讲了什么\n\n正文\n\n## 二级也算")
        assertEquals(Block.Heading("讲了什么"), blocks[0])
        assertEquals(Block.Paragraph("正文"), blocks[1])
        assertEquals(Block.Heading("二级也算"), blocks[2])
    }

    @Test
    fun 井号后无空格是正文不是标题_Sourcery审查回归() {
        // "#1 ranked" / "#ai" 类正文行不得被误判为标题
        val blocks = parseBlocks("#1 ranked on HN today\n\n#ai is trending")
        assertEquals(
            listOf<Block>(
                Block.Paragraph("#1 ranked on HN today"),
                Block.Paragraph("#ai is trending"),
            ),
            blocks,
        )
    }

    @Test
    fun 列表与续行合并() {
        val blocks = parseBlocks("- 第一项\n- 第二项\n  换行续写\n\n段落")
        assertEquals(Block.Bullets(listOf("第一项", "第二项 换行续写")), blocks[0])
        assertEquals(Block.Paragraph("段落"), blocks[1])
    }

    @Test
    fun 行内斜体与粗体解析_未闭合按字面输出() {
        val italic = parseInline("引用 *a quote* 结束")
        assertEquals("引用 a quote 结束", italic.text)
        assertTrue(italic.spanStyles.isNotEmpty())

        val bold = parseInline("**重点** 之后")
        assertEquals("重点 之后", bold.text)

        val unclosed = parseInline("5 * 3 = 15")
        assertEquals("5 * 3 = 15", unclosed.text)
    }
}
