package whl.trending.chat.engine

import whl.trending.chat.model.ChatMessage

/**
 * 聊天引擎抽象。Demo 注入 [whl.trending.chat.sample.FakeChatEngine]，
 * 正式注入 [ChatApi]，UI 层零改动即可切换。
 */
interface ChatEngine {
    /**
     * 发送一轮对话，流式返回助手回复（Markdown 源串）。
     *
     * @param history 截至当前的完整消息历史（含本轮用户消息，不含流式占位）
     * @param onDelta 增量回调，按到达顺序携带片段文本（调用线程不保证）
     * @throws ChatException 任何失败（HTTP 非 2xx / 传输异常 / 中途断流），携带分类后的
     *   [whl.trending.chat.model.ChatError]；中途断流按可重试处理，已渲染部分由调用方丢弃
     */
    suspend fun send(
        history: List<ChatMessage>,
        onDelta: (String) -> Unit = {},
        search: Boolean = false,
        onSearch: (whl.trending.chat.model.SearchEvent) -> Unit = {},
    ): String
}
