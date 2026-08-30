package whl.trending.chat.ui

import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard

internal actual suspend fun copyPlainText(clipboard: Clipboard, text: String) {
    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("chat", text)))
}
