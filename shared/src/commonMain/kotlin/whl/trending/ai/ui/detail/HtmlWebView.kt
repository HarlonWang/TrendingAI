package whl.trending.ai.ui.detail

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

data class WebViewColors(
    val bg: String,
    val text: String,
    val codeBg: String,
    val border: String,
    val link: String,
    val muted: String,
)

/**
 * 加载 HTML 字符串的 WebView，支持通过 [WebViewColors] 注入主题色。
 *
 * 用于展示应用内生成的 HTML 内容（如 GitHub README），
 * 通过 CSS 注入确保样式与当前主题一致。
 * 如需加载远程 URL，使用 [whl.trending.ai.ui.webview.UrlWebView]。
 */
@Composable
expect fun HtmlWebView(html: String, colors: WebViewColors, modifier: Modifier = Modifier)

internal fun wrapHtml(body: String, colors: WebViewColors): String {
    val textColor   = colors.text
    val bgColor     = colors.bg
    val codeBg      = colors.codeBg
    val borderColor = colors.border
    val linkColor   = colors.link
    val mutedColor  = colors.muted

    return buildString {
        appendLine("<!DOCTYPE html>")
        appendLine("<html><head>")
        appendLine("""<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">""")
        appendLine("<style>")
        appendLine("""
            html, body {
                max-width: 100%;
                overflow-x: hidden;
            }
            body {
                font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
                color: $textColor;
                background: $bgColor;
                padding: 16px;
                line-height: 1.6;
                font-size: 15px;
                margin: 0;
                word-wrap: break-word;
                overflow-wrap: break-word;
            }
            img { max-width: 100%; height: auto; display: inline-block; vertical-align: middle; }
            a { color: $linkColor; }
            h1, h2 { border-bottom: 1px solid $borderColor; padding-bottom: 8px; }
            pre {
                overflow-x: auto;
                background: $codeBg;
                padding: 12px;
                border-radius: 6px;
                font-size: 13px;
            }
            code {
                font-family: monospace;
                font-size: 0.9em;
                background: $codeBg;
                padding: 2px 4px;
                border-radius: 3px;
            }
            pre code { background: none; padding: 0; }
            table { border-collapse: collapse; width: 100%; display: block; overflow-x: auto; }
            th, td { border: 1px solid $borderColor; padding: 8px 12px; text-align: left; }
            th { background: $codeBg; }
            blockquote {
                border-left: 4px solid $borderColor;
                margin: 0;
                padding: 0 16px;
                color: $mutedColor;
            }
        """.trimIndent())
        appendLine("</style></head>")
        appendLine("<body>")
        appendLine(body)
        appendLine("</body></html>")
    }
}
