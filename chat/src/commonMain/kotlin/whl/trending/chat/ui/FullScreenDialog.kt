package whl.trending.chat.ui

import androidx.compose.runtime.Composable

/**
 * 铺满整屏、画到系统栏下面的 Dialog，系统栏图标深浅跟随当前配色。
 * Android 需要关掉 decorFitsSystemWindows 并手动设置窗口的图标外观；iOS 的 Dialog 天然全屏。
 */
@Composable
internal expect fun FullScreenDialog(onDismiss: () -> Unit, content: @Composable () -> Unit)
