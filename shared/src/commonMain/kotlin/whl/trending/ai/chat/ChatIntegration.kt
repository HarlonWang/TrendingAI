package whl.trending.ai.chat

import androidx.compose.runtime.Composable

/**
 * 进入聊天时可携带的初始上下文（项目 / HN / PH 条目）。
 * 通用 AI 助手入口传 null。
 */
data class ChatContext(
    val title: String,
    val summary: String? = null,
    val sourceUrl: String? = null,
)

/**
 * Android-only ChatScreen 通过此全局 slot 注入 CMP 导航。
 * 未注册时为 null（等价 NoOp），与 [whl.trending.ai.update.UpdateChecker] 采用同一套依赖反转范式。
 */
var globalChatScreen: (@Composable (context: ChatContext?, onBack: () -> Unit) -> Unit)? = null
