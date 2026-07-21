package whl.trending.chat.model

/**
 * 会话能力模式（EchoFlow 的单选互斥范式）：sealed 单值天然排斥「两个开关同时亮」。
 * P2 仅 WebSearch；P3 加 DeepResearch 时只增枚举值，toggle 逻辑不变。
 */
sealed interface ChatMode {
    data object Normal : ChatMode
    data object WebSearch : ChatMode
    data object DeepResearch : ChatMode
}

/** Deep Research 任务状态（引擎轮询返回） */
data class ResearchRun(val id: String, val status: String, val report: String?, val error: String?)

/** 引用来源（服务端按 url 去重后下发；随 assistant 消息持久化） */
data class SourceRef(val title: String, val url: String)

/** 流式过程中的搜索事件（引擎 → VM），与服务端 SSE 协议一一对应 */
sealed interface SearchEvent {
    data object Started : SearchEvent
    data class Done(val query: String?) : SearchEvent
    data class Source(val title: String, val url: String) : SearchEvent
}
