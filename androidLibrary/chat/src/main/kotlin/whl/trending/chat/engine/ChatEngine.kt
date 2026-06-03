package whl.trending.chat.engine

import whl.trending.ai.chat.ChatContext
import whl.trending.chat.model.ChatMessage

/**
 * 聊天引擎抽象。Demo 注入 [whl.trending.chat.sample.FakeChatEngine]，
 * 正式注入 [ChatApi]，UI 层零改动即可切换。
 */
interface ChatEngine {
    /**
     * 发送一轮对话，返回助手回复（Markdown 源串）。
     *
     * @param history 截至当前的完整消息历史（含本轮用户消息）
     * @param context 可选初始上下文
     * @throws ChatException 任何失败（HTTP 非 2xx / 传输异常），携带分类后的 [whl.trending.chat.model.ChatError]
     */
    suspend fun send(history: List<ChatMessage>, context: ChatContext?): String
}
