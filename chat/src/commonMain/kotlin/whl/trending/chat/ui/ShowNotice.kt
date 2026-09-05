package whl.trending.chat.ui

import androidx.compose.runtime.Composable

/** 轻量即时提示（Android Toast 形态），用于不值得打断的失败反馈。 */
@Composable
internal expect fun rememberShowNotice(): (String) -> Unit
