package whl.trending.ai.ui.webview

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun UrlWebView(url: String, onPageFinished: () -> Unit = {}, modifier: Modifier = Modifier)
