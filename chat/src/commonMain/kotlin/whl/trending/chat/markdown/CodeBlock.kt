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
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.BoldHighlight
import dev.snipme.highlights.model.ColorHighlight
import dev.snipme.highlights.model.SyntaxLanguage
import dev.snipme.highlights.model.SyntaxTheme
import whl.trending.chat.ui.CopyIconButton

/**
 * 代码块：等宽字体 + 横向滚动 + 语言标签 + 复制按钮 + 语法高亮。
 *
 * 高亮由 `dev.snipme:highlights`（纯 KMP，真词法分析）产出 token 区间，
 * 配色从 [SyntaxTheme] 映射回当前 M3 [ColorScheme]，随明暗主题联动、与全 app 色调统一。
 */
@Composable
fun CodeBlock(code: String, language: String) {
    val colors = MaterialTheme.colorScheme
    val theme = m3SyntaxTheme(colors)
    val highlighted = remember(code, language, theme) {
        highlightCode(code = code, language = language, theme = theme)
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
            CopyIconButton(
                text = code,
                icon = Icons.Filled.ContentCopy,
                iconSize = 24.dp,
            )
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

/** 把 M3 语义色映射成 highlights 的 [SyntaxTheme]（value 相等 → 可直接作 remember key）。 */
private fun m3SyntaxTheme(colors: ColorScheme): SyntaxTheme = SyntaxTheme(
    key = "m3",
    code = colors.onSurface.toArgb(),
    keyword = colors.primary.toArgb(),
    string = colors.tertiary.toArgb(),
    literal = colors.secondary.toArgb(),
    comment = colors.outline.toArgb(),
    metadata = colors.primary.toArgb(),
    multilineComment = colors.outline.toArgb(),
    // 标点/标记不特殊着色，跟随正文色，避免整段代码被染得过花
    punctuation = colors.onSurface.toArgb(),
    mark = colors.onSurface.toArgb(),
)

/** 常见语言短别名 → highlights 枚举（[SyntaxLanguage.getByName] 只按枚举全名精确匹配）。 */
private val LANGUAGE_ALIASES = mapOf(
    "kt" to SyntaxLanguage.KOTLIN, "kts" to SyntaxLanguage.KOTLIN,
    "js" to SyntaxLanguage.JAVASCRIPT, "mjs" to SyntaxLanguage.JAVASCRIPT, "jsx" to SyntaxLanguage.JAVASCRIPT,
    "ts" to SyntaxLanguage.TYPESCRIPT, "tsx" to SyntaxLanguage.TYPESCRIPT,
    "py" to SyntaxLanguage.PYTHON,
    "rb" to SyntaxLanguage.RUBY,
    "rs" to SyntaxLanguage.RUST,
    "sh" to SyntaxLanguage.SHELL, "bash" to SyntaxLanguage.SHELL, "zsh" to SyntaxLanguage.SHELL, "shell" to SyntaxLanguage.SHELL,
    "c++" to SyntaxLanguage.CPP, "cc" to SyntaxLanguage.CPP, "cxx" to SyntaxLanguage.CPP,
    "cs" to SyntaxLanguage.CSHARP,
)

private fun resolveLanguage(language: String): SyntaxLanguage {
    val key = language.trim().lowercase()
    if (key.isEmpty()) return SyntaxLanguage.DEFAULT
    return LANGUAGE_ALIASES[key] ?: SyntaxLanguage.getByName(key) ?: SyntaxLanguage.DEFAULT
}

/** 用 highlights 产出的 token 区间构建 [AnnotatedString]；解析异常时 `runCatching` 兜底为纯文本。 */
private fun highlightCode(code: String, language: String, theme: SyntaxTheme): AnnotatedString {
    val highlights = runCatching {
        Highlights.Builder()
            .code(code)
            .language(resolveLanguage(language))
            .theme(theme)
            .build()
            .getHighlights()
    }.getOrDefault(emptyList())

    return buildAnnotatedString {
        append(code)
        highlights.forEach { highlight ->
            val start = highlight.location.start.coerceIn(0, code.length)
            val end = highlight.location.end.coerceIn(start, code.length)
            if (start == end) return@forEach
            when (highlight) {
                is ColorHighlight -> addStyle(SpanStyle(color = Color(highlight.rgb)), start, end)
                is BoldHighlight -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
            }
        }
    }
}
