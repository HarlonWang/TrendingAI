package whl.trending.chat.ui

import androidx.compose.runtime.Composable

/** 把一张网络图片交给系统分享面板（保存到相册走系统面板，不另申请相册写权限）。下载失败静默不弹。 */
@Composable
internal expect fun rememberShareImageUrl(): (String) -> Unit
