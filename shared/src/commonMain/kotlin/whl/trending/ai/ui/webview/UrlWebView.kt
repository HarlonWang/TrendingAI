package whl.trending.ai.ui.webview

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 加载远程 URL 的 WebView。
 *
 * 用于展示外部网页（如隐私政策），无法控制页面内容样式。
 * 如需加载 HTML 字符串并自定义样式，使用 [whl.trending.ai.ui.detail.HtmlWebView]。
 */
@Composable
expect fun UrlWebView(url: String, onPageFinished: () -> Unit = {}, modifier: Modifier = Modifier)
