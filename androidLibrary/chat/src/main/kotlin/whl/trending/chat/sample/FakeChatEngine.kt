package whl.trending.chat.sample

import kotlinx.coroutines.delay
import whl.trending.ai.chat.ChatContext
import whl.trending.chat.engine.ChatEngine
import whl.trending.chat.engine.DetailSummaryResult
import whl.trending.chat.model.ChatMessage

/**
 * 离线假引擎：不接 API，模拟逐字流式输出预置 Markdown，
 * 用于跑通「输入 → 发送 → 流式渲染」完整交互链路。
 */
class FakeChatEngine(
    private val chunkDelayMillis: Long = 30L,
) : ChatEngine {

    private val replies = listOf(
        SampleData.richMarkdown,
        "收到 👍 这是一段**纯文本 + `行内代码`**的简短回复，用来演示不同长度消息的排版。\n\n" +
            "- 列表项一\n- 列表项二\n\n```bash\n# 也能渲染命令\n./gradlew :androidLibrary:chat:assembleDebug\n```",
    )
    private var index = 0

    override suspend fun send(
        history: List<ChatMessage>,
        context: ChatContext?,
        onDelta: (String) -> Unit,
    ): String {
        val reply = replies[index % replies.size]
        index++
        return streamOut(reply, onDelta)
    }

    override suspend fun sendDetailSummary(
        context: ChatContext,
        onDelta: (String) -> Unit,
    ): DetailSummaryResult {
        val reply = "### 这是什么\n\n${context.title} 的模拟详细解读。\n\n" + SampleData.richMarkdown
        return DetailSummaryResult(streamOut(reply, onDelta), cached = false)
    }

    /** 按小块模拟逐字输出 */
    private suspend fun streamOut(reply: String, onDelta: (String) -> Unit): String {
        reply.chunked(24).forEach { chunk ->
            delay(chunkDelayMillis)
            onDelta(chunk)
        }
        return reply
    }
}
