package whl.trending.ai.ui.webview

import androidx.compose.runtime.Composable
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import wang.harlon.webview.core.WebViewConfig
import wang.harlon.webview.core.rememberWebViewState
import wang.harlon.webview.WebViewScreen as KmpWebViewScreen

/**
 * 应用内网页浏览，基于 kmp-webview 库。
 *
 * @param title 非空时固定显示该标题；为空时跟随网页自身标题动态变化。
 */
@Composable
fun WebViewScreen(url: String, title: String, onBack: () -> Unit) {
    val state = rememberWebViewState(url)

    // 系统返回键/手势：先回退网页历史，退到底再交给路由出栈。
    // 与 NavDisplay 共用同一 NavigationEventDispatcher，组合中后注册的 enabled handler 优先。
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = state.canGoBack,
        onBackCompleted = { state.goBack() },
    )

    KmpWebViewScreen(
        state = state,
        config = WebViewConfig(
            titleOverride = title.takeIf { it.isNotBlank() },
            // 纯内容浏览场景，关闭文件选择与音视频采集能力
            allowFileChooser = false,
            allowCameraCapture = false,
            allowMediaCapture = false,
        ),
        onCloseRequest = onBack,
    )
}
