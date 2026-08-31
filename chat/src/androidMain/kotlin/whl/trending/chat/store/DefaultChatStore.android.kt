package whl.trending.chat.store

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.File
import whl.trending.chat.db.chatDatabase

@Composable
internal actual fun rememberDefaultChatStore(): ChatStore {
    val appContext = LocalContext.current.applicationContext
    return remember {
        ChatStore(chatDatabase(appContext), File(appContext.filesDir, "chat_images").absolutePath)
    }
}
