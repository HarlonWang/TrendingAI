package whl.trending.ai.ui.detail

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGRectMake
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun HtmlWebView(html: String, colors: WebViewColors, modifier: Modifier) {
    UIKitView(
        factory = {
            WKWebView(
                frame = CGRectMake(0.0, 0.0, 0.0, 0.0),
                configuration = WKWebViewConfiguration()
            )
        },
        update = { webView ->
            webView.loadHTMLString(wrapHtml(html, colors), baseURL = null)
        },
        modifier = modifier
    )
}
