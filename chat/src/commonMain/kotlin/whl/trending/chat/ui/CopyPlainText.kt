package whl.trending.chat.ui

import androidx.compose.ui.platform.Clipboard

/** 写纯文本进系统剪贴板（ClipEntry 各平台构造不同，CMP 无公共工厂）。 */
internal expect suspend fun copyPlainText(clipboard: Clipboard, text: String)
