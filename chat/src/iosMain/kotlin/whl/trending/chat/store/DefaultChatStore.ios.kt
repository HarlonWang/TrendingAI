package whl.trending.chat.store

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import whl.trending.chat.db.chatDatabase

@Composable
internal actual fun rememberDefaultChatStore(): ChatStore =
    remember { RoomChatStore(chatDatabase(), documentsPath() + "/chat_images") }

@OptIn(ExperimentalForeignApi::class)
private fun documentsPath(): String {
    val documents = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )
    return requireNotNull(documents).path!!
}
