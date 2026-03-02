package whl.trending.ai.ui.detail

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIColor
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun HtmlWebView(html: String, colors: WebViewColors, modifier: Modifier) {
    UIKitView(
        factory = {
            val bgColor = colors.bg.toUIColor()
            WKWebView(
                frame = CGRectMake(0.0, 0.0, 0.0, 0.0),
                configuration = WKWebViewConfiguration()
            ).apply {
                opaque = false
                backgroundColor = bgColor
                scrollView.backgroundColor = bgColor
            }
        },
        update = { webView ->
            webView.loadHTMLString(wrapHtml(html, colors), baseURL = null)
        },
        modifier = modifier
    )
}

private fun String.toUIColor(): UIColor {
    val hex = removePrefix("#")
    if (hex.length != 6) return UIColor.clearColor

    val red = hex.substring(0, 2).toIntOrNull(16) ?: return UIColor.clearColor
    val green = hex.substring(2, 4).toIntOrNull(16) ?: return UIColor.clearColor
    val blue = hex.substring(4, 6).toIntOrNull(16) ?: return UIColor.clearColor

    return UIColor.colorWithRed(
        red = red / 255.0,
        green = green / 255.0,
        blue = blue / 255.0,
        alpha = 1.0
    )
}
