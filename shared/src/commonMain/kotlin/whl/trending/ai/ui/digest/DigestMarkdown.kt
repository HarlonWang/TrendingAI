package whl.trending.ai.ui.digest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * digest 正文的受控子集 markdown 渲染。
 *
 * 不引第三方渲染库的依据：解读由我们自己的 prompt 生成，结构受控——
 * 只有 ### 三级标题、段落、无序列表和粗体、斜体两种行内标记
 * （引用评论用斜体，见后端 digest/prompt.js 的硬约束）。
 * 子集之外的语法按纯文本降级展示，不报错不吞内容。
 * chat 模块的 MarkdownText 基于 commonmark（Java 库），进不了 commonMain，不能复用。
 */

internal sealed interface Block {
    data class Heading(val text: String) : Block
    data class Paragraph(val text: String) : Block
    data class Bullets(val items: List<String>) : Block
}

// ATX 标题要求「井号 + 空格」：不带空格的 "#1 ranked"、"#ai" 是正文不是标题。
// 级别刻意不限（模型偶发 ##/# 时按标题渲染，优于把井号字面量端给用户）
private val HEADING_REGEX = Regex("""^#{1,6}\s+(.+)""")

internal fun parseBlocks(markdown: String): List<Block> {
    val blocks = mutableListOf<Block>()
    val paragraph = StringBuilder()
    val bullets = mutableListOf<String>()

    fun flushParagraph() {
        if (paragraph.isNotBlank()) blocks.add(Block.Paragraph(paragraph.toString().trim()))
        paragraph.clear()
    }

    fun flushBullets() {
        if (bullets.isNotEmpty()) blocks.add(Block.Bullets(bullets.toList()))
        bullets.clear()
    }

    for (line in markdown.lines()) {
        val trimmed = line.trim()
        val heading = HEADING_REGEX.matchEntire(trimmed)
        when {
            trimmed.isEmpty() -> {
                flushParagraph(); flushBullets()
            }
            heading != null -> {
                flushParagraph(); flushBullets()
                blocks.add(Block.Heading(heading.groupValues[1].trim()))
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                flushParagraph()
                bullets.add(trimmed.substring(2).trim())
            }
            else -> {
                if (bullets.isNotEmpty()) {
                    // 列表项换行续行：并入上一项
                    bullets[bullets.lastIndex] = bullets.last() + " " + trimmed
                } else {
                    if (paragraph.isNotEmpty()) paragraph.append(' ')
                    paragraph.append(trimmed)
                }
            }
        }
    }
    flushParagraph(); flushBullets()
    return blocks
}

/** 行内 **粗体** / *斜体*；未闭合的标记按字面输出 */
internal fun parseInline(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end > i + 1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(text[i]); i++
                }
            }
            text[i] == '*' -> {
                val end = text.indexOf('*', i + 1)
                if (end > i) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text[i]); i++
                }
            }
            else -> {
                append(text[i]); i++
            }
        }
    }
}

@Composable
fun DigestMarkdown(markdown: String, modifier: Modifier = Modifier) {
    val blocks = remember(markdown) { parseBlocks(markdown) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is Block.Heading -> Text(
                    text = parseInline(block.text),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 10.dp)
                )
                is Block.Paragraph -> Text(
                    text = parseInline(block.text),
                    fontSize = 14.sp,
                    lineHeight = 24.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                is Block.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    block.items.forEach { item ->
                        Row {
                            Text(
                                text = "•",
                                fontSize = 14.sp,
                                lineHeight = 24.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = parseInline(item),
                                fontSize = 14.sp,
                                lineHeight = 24.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
