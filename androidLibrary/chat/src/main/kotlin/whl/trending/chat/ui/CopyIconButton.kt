package whl.trending.chat.ui

import android.content.ClipData
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import whl.trending.chat.R

/** 复制成功后图标停留在「对勾」的时长 */
private val COPIED_FEEDBACK_DURATION = 1500.milliseconds

/**
 * 复制按钮：点击后**就地**把图标换成对勾并震一下，1.5s 后复原。
 *
 * 反馈落在手指刚离开的位置，不需要用户把视线转到屏幕角落。
 * 早先的做法是 Android 13+ 靠系统剪贴板浮层、13 以下补 Toast，两个问题：
 * 浮层固定在屏幕左下角，聊天页那里正好是输入栏、键盘弹起时更会被压住；
 * 且国内 ROM 对该浮层的显隐策略并不一致，等于部分机型点了完全无反馈。
 * 代码块的复制按钮则连 Toast 分支都没有，任何版本都静默。现在两处统一走这里。
 */
@Composable
fun CopyIconButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.ContentCopy,
    iconSize: Dp = 18.dp,
) {
    val clipboard = LocalClipboard.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(COPIED_FEEDBACK_DURATION)
            copied = false
        }
    }

    IconButton(
        onClick = {
            scope.launch {
                // 写剪贴板失败时不点亮对勾——否则是「显示成功但其实没复制」
                runCatching {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("chat", text)))
                }.onSuccess {
                    copied = true
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                }
            }
        },
        modifier = modifier,
    ) {
        Crossfade(targetState = copied, label = "copy") { done ->
            Icon(
                imageVector = if (done) Icons.Outlined.Check else icon,
                // 状态同步进语义树，读屏用户也能听到「已复制」
                contentDescription = stringResource(
                    if (done) R.string.chat_copied else R.string.chat_copy,
                ),
                tint = if (done) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(iconSize),
            )
        }
    }
}
