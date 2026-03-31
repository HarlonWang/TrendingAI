package whl.trending.ai.ui.webview

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun UrlWebView(url: String, onPageFinished: () -> Unit, modifier: Modifier) {
    UIKitView(
        factory = {
            WKWebView(
                frame = CGRectMake(0.0, 0.0, 0.0, 0.0),
                configuration = WKWebViewConfiguration()
            ).apply {
                navigationDelegate = object : NSObject(), WKNavigationDelegateProtocol {
                    override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
                        onPageFinished()
                    }
                }
                NSURL.URLWithString(url)?.let { nsUrl ->
                    loadRequest(NSURLRequest.requestWithURL(nsUrl))
                }
            }
        },
        modifier = modifier
    )
}
