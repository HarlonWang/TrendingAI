package whl.trending.chat.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.ClipData
import kotlinx.coroutines.launch

/**
 * 代码块：等宽字体 + 横向滚动 + 语言标签 + 复制按钮 + 轻量语法高亮。
 *
 * 高亮为轻量正则方案，覆盖少数常见语言；未命中语言走通用（字符串/数字/注释）或纯文本。
 * 后续若需专业多语言高亮，可按 Claude 方案接入 JavaScriptEngine + highlight.js。
 */
@Composable
fun CodeBlock(code: String, language: String) {
    val colors = MaterialTheme.colorScheme
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val highlighted = remember(
        code,
        language,
        colors.primary,
        colors.tertiary,
        colors.secondary,
        colors.outline,
    ) {
        SyntaxHighlighter.highlight(
            code = code,
            language = language,
            keyword = colors.primary,
            string = colors.tertiary,
            number = colors.secondary,
            comment = colors.outline,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = language.ifBlank { "code" },
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
            IconButton(onClick = {
                scope.launch {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("code", code)))
                }
            }) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = "Copy code",
                    tint = colors.onSurfaceVariant,
                )
            }
        }
        Text(
            text = highlighted,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            color = colors.onSurface,
        )
    }
}

/** 轻量正则语法高亮。 */
private object SyntaxHighlighter {

    private val KEYWORDS: Map<String, Set<String>> = mapOf(
        "kotlin" to setOf(
            "fun", "val", "var", "class", "object", "interface", "if", "else", "when", "for", "while",
            "return", "import", "package", "private", "public", "internal", "override", "suspend", "data",
            "sealed", "enum", "companion", "is", "as", "in", "null", "true", "false", "this", "by", "lateinit",
        ),
        "java" to setOf(
            "public", "private", "protected", "class", "interface", "void", "int", "long", "boolean", "new",
            "return", "if", "else", "for", "while", "import", "package", "static", "final", "this", "null",
            "true", "false", "extends", "implements", "throws",
        ),
        "json" to setOf("true", "false", "null"),
        "bash" to setOf("if", "then", "else", "fi", "for", "do", "done", "echo", "export", "cd", "function"),
        "python" to setOf(
            "def", "class", "import", "from", "return", "if", "elif", "else", "for", "while", "in", "is",
            "None", "True", "False", "self", "lambda", "with", "as", "try", "except", "pass",
        ),
    )

    private val ALIASES = mapOf(
        "kt" to "kotlin", "kts" to "kotlin",
        "js" to "js", "javascript" to "js", "ts" to "js", "typescript" to "js",
        "sh" to "bash", "shell" to "bash", "zsh" to "bash",
        "py" to "python",
    )

    // 通用：字符串、注释、数字
    private val tokenRegex = Regex(
        """(?<comment>//[^\n]*|#[^\n]*|/\*[\s\S]*?\*/)""" +
            """|(?<string>"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|`(?:\\.|[^`\\])*`)""" +
            """|(?<number>\b\d+(?:\.\d+)?\b)""" +
            """|(?<word>[A-Za-z_]\w*)""",
    )

    fun highlight(
        code: String,
        language: String,
        keyword: Color,
        string: Color,
        number: Color,
        comment: Color,
    ): AnnotatedString {
        val lang = ALIASES[language.lowercase()] ?: language.lowercase()
        val keywords = KEYWORDS[lang] ?: emptySet()
        // js 没有内置关键字表也走通用高亮（字符串/数字/注释）
        return buildAnnotatedString {
            var last = 0
            for (m in tokenRegex.findAll(code)) {
                if (m.range.first > last) append(code.substring(last, m.range.first))
                val value = m.value
                val color = when {
                    m.groups["comment"] != null -> comment
                    m.groups["string"] != null -> string
                    m.groups["number"] != null -> number
                    m.groups["word"] != null && value in keywords -> keyword
                    else -> null
                }
                if (color != null) withStyle(SpanStyle(color = color)) { append(value) } else append(value)
                last = m.range.last + 1
            }
            if (last < code.length) append(code.substring(last))
        }
    }
}
