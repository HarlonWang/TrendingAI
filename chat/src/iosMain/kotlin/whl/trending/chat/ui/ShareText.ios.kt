package whl.trending.chat.ui

import androidx.compose.runtime.Composable
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

@Composable
internal actual fun rememberShareText(): (String) -> Unit = { text ->
    topViewController()?.presentViewController(
        UIActivityViewController(activityItems = listOf(text), applicationActivities = null),
        animated = true,
        completion = null,
    )
}

internal fun topViewController(): platform.UIKit.UIViewController? {
    var top = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (top?.presentedViewController != null) {
        top = top.presentedViewController
    }
    return top
}
