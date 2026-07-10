package whl.trending.chat.engine

import whl.trending.ai.chat.ChatContext
import whl.trending.chat.model.ChatMessage

/** 一次详细解读请求的结果：全文 + 是否缓存命中（驱动 detail_summary_cache_hit 埋点）。 */
data class DetailSummaryResult(val content: String, val cached: Boolean)

/**
 * 聊天引擎抽象。Demo 注入 [whl.trending.chat.sample.FakeChatEngine]，
 * 正式注入 [ChatApi]，UI 层零改动即可切换。两个方法均为流式：
 * [onDelta] 随增量到达被调用（调用线程不保证），返回值是最终全文。
 */
interface ChatEngine {
    /**
     * 发送一轮对话，流式返回助手回复（Markdown 源串）。
     *
     * @param history 截至当前的完整消息历史（含本轮用户消息，不含流式占位）
     * @param context 可选初始上下文
     * @param onDelta 增量回调，按到达顺序携带片段文本
     * @throws ChatException 任何失败（HTTP 非 2xx / 传输异常 / 中途断流），携带分类后的
     *   [whl.trending.chat.model.ChatError]；中途断流按可重试处理，已渲染部分由调用方丢弃
     */
    suspend fun send(
        history: List<ChatMessage>,
        context: ChatContext?,
        onDelta: (String) -> Unit = {},
    ): String

    /**
     * 请求「一键详细解读」（`POST /api/detail-summary`），流式返回解读全文。
     * 缓存命中时服务端以同一 SSE 格式一次性推完，[DetailSummaryResult.cached] 为 true。
     *
     * @param context 必须携带 source/externalId（[whl.trending.chat.DetailSummaryPolicy] 已保证）
     * @throws ChatException 失败分类同 [send]；403 `login_required` 为登录闸（匿名生成不可用）
     */
    suspend fun sendDetailSummary(
        context: ChatContext,
        onDelta: (String) -> Unit = {},
    ): DetailSummaryResult
}
