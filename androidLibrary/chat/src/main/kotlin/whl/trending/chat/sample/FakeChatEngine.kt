package whl.trending.chat.sample

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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

    /** 分片 emit 模拟打字机：先思考一段，再按词逐块吐出，演示流式渲染。 */
    override fun send(history: List<ChatMessage>, context: ChatContext?): Flow<String> = flow {
        delay(delayMillis)
        val reply = replies[index % replies.size]
        index++
        reply.chunked(8).forEach { chunk ->
            emit(chunk)
            delay(24L)
        }
    }
}
