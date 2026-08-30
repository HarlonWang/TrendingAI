package whl.trending.ai.chat

import androidx.compose.runtime.Composable
import whl.trending.chat.ChatContext

/**
 * ChatScreen（暂为 chat SDK 的 androidMain）通过此全局 slot 注入 CMP 导航；
 * ChatScreen 进 commonMain 后本 slot 退役、导航直调。
 * 未注册时为 null（等价 NoOp），与 [whl.trending.ai.update.UpdateChecker] 采用同一套依赖反转范式。
 */
var globalChatScreen: (@Composable (context: ChatContext?, onBack: () -> Unit) -> Unit)? = null
