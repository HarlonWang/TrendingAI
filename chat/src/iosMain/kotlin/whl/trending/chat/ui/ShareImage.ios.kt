package whl.trending.chat.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIImage

@Composable
internal actual fun rememberShareImageUrl(): (String) -> Unit {
    val scope = rememberCoroutineScope()
    return { url ->
        scope.launch {
            val image = withContext(Dispatchers.Default) {
                NSURL.URLWithString(url)?.let { NSData.dataWithContentsOfURL(it) }?.let { UIImage.imageWithData(it) }
            } ?: return@launch
            topViewController()?.presentViewController(
                UIActivityViewController(activityItems = listOf(image), applicationActivities = null),
                animated = true,
                completion = null,
            )
        }
    }
}
