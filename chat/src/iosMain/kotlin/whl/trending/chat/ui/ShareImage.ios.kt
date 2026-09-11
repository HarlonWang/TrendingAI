package whl.trending.chat.ui

import androidx.compose.runtime.Composable
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.addressOf
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIImage

@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun rememberShareImageBytes(): suspend (ByteArray) -> Unit = { bytes ->
    val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
    UIImage.imageWithData(data)?.let { image ->
        topViewController()?.presentViewController(
            UIActivityViewController(activityItems = listOf(image), applicationActivities = null),
            animated = true,
            completion = null,
        )
    }
}
