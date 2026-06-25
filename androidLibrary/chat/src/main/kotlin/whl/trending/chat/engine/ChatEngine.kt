package whl.trending.chat.engine

import kotlinx.coroutines.flow.Flow
import whl.trending.ai.chat.ChatContext
import whl.trending.chat.model.ChatMessage

/**
 * 聊天引擎抽象。Demo 注入 [whl.trending.chat.sample.FakeChatEngine]，
 * 正式注入 [ChatApi] 或 BYOK 直连引擎，UI 层零改动即可切换。
 */
interface ChatEngine {
    /**
     * 发送一轮对话，返回助手回复的**增量流**（每个元素是一段新增 Markdown 文本，按序拼接为完整回复）。
     *
     * 流式引擎逐字 emit；非流式引擎（如 [ChatApi]）拿到整段后一次性 emit 整串——
     * 非流式天然是"一次发射的流"，调用方统一按流处理。
     *
     * @param history 截至当前的完整消息历史（含本轮用户消息）
     * @param context 可选初始上下文
     * @throws ChatException 流收集过程中任何失败（HTTP 非 2xx / 传输异常），携带分类后的 [whl.trending.chat.model.ChatError]
     */
    fun send(history: List<ChatMessage>, context: ChatContext?): Flow<String>
}
