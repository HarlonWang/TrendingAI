package whl.trending.chat.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.commonmark.node.Node
import org.commonmark.node.Text as CmText

class MarkdownImagesTest {

    private fun firstParagraph(markdown: String): Node = MarkdownParser.parse(markdown).firstChild!!

    /** 把 Text 片段的节点串回纯文本,便于断言。 */
    private fun InlineSegment.Text.plainText(): String = buildString {
        fun walk(node: Node) {
            if (node is CmText) append(node.literal)
            var child = node.firstChild
            while (child != null) {
                walk(child)
                child = child.next
            }
        }
        nodes.forEach { walk(it) }
    }

    @Test
    fun `plain paragraph yields single text segment`() {
        val segments = splitByImages(firstParagraph("hello **world**"))

        assertEquals(1, segments.size)
        val text = assertIs<InlineSegment.Text>(segments[0])
        assertEquals("hello world", text.plainText())
    }

    @Test
    fun `image-only paragraph yields single image segment`() {
        val segments = splitByImages(firstParagraph("![screenshot](https://example.com/a.png)"))

        assertEquals(listOf(InlineSegment.Image("https://example.com/a.png", "screenshot")), segments)
    }

    @Test
    fun `text before and after image is split into segments`() {
        val segments = splitByImages(firstParagraph("看截图 ![shot](https://example.com/a.png) 如上"))

        assertEquals(3, segments.size)
        assertEquals("看截图 ", assertIs<InlineSegment.Text>(segments[0]).plainText())
        assertEquals(InlineSegment.Image("https://example.com/a.png", "shot"), segments[1])
        assertEquals(" 如上", assertIs<InlineSegment.Text>(segments[2]).plainText())
    }

    @Test
    fun `consecutive images yield consecutive image segments`() {
        val segments = splitByImages(
            firstParagraph("![a](https://example.com/a.png)![b](http://example.com/b.png)"),
        )

        assertEquals(
            listOf(
                InlineSegment.Image("https://example.com/a.png", "a"),
                InlineSegment.Image("http://example.com/b.png", "b"),
            ),
            segments,
        )
    }

    @Test
    fun `relative path image stays in text segment`() {
        val segments = splitByImages(firstParagraph("前缀 ![alt text](docs/a.png) 后缀"))

        assertEquals(1, segments.size)
        val text = assertIs<InlineSegment.Text>(segments[0])
        assertEquals("前缀 alt text 后缀", text.plainText())
    }

    @Test
    fun `empty alt falls back to empty string`() {
        val segments = splitByImages(firstParagraph("![](https://example.com/a.png)"))

        assertEquals(listOf(InlineSegment.Image("https://example.com/a.png", "")), segments)
    }

    @Test
    fun `styled alt text is flattened to plain text`() {
        val segments = splitByImages(firstParagraph("![**bold** alt](https://example.com/a.png)"))

        val image = assertIs<InlineSegment.Image>(segments.single())
        assertEquals("bold alt", image.alt)
    }

    @Test
    fun `non-http scheme stays in text segment`() {
        val segments = splitByImages(firstParagraph("![f](file:///sdcard/a.png)"))

        assertTrue(segments.single() is InlineSegment.Text)
    }
}
