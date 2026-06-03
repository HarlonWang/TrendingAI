package whl.trending.chat.sample

import kotlinx.coroutines.delay
import whl.trending.ai.chat.ChatContext
import whl.trending.chat.engine.ChatEngine
import whl.trending.chat.model.ChatMessage

/**
 * 离线假引擎：不接 API，发送后延迟一段返回预置 Markdown，
 * 用于跑通「输入 → 发送 → 思考中 → 渲染」完整交互链路。
 */
class FakeChatEngine(
    private val delayMillis: Long = 1200L,
) : ChatEngine {

    private val replies = listOf(
        SampleData.richMarkdown,
        "收到 👍 这是一段**纯文本 + `行内代码`**的简短回复，用来演示不同长度消息的排版。\n\n" +
            "- 列表项一\n- 列表项二\n\n```bash\n# 也能渲染命令\n./gradlew :androidLibrary:chat:assembleDebug\n```",
    )
    private var index = 0

    override suspend fun send(history: List<ChatMessage>, context: ChatContext?): String {
        delay(delayMillis)
        val reply = replies[index % replies.size]
        index++
        return reply
    }
}
