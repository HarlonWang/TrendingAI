package whl.trending.chat.model

/** 引用来源（服务端按 url 去重后下发；随 assistant 消息持久化） */
data class SourceRef(val title: String, val url: String)

/** 流式过程中的搜索事件（引擎 → VM），与服务端 SSE 协议一一对应 */
sealed interface SearchEvent {
    data object Started : SearchEvent
    data class Done(val query: String?) : SearchEvent
    data class Source(val title: String, val url: String) : SearchEvent
}

/** 流式过程中的生图进度（引擎 → VM）。图片本身不走事件：服务端以 Markdown 图片作为 delta 下发 */
sealed interface ImageGenerationEvent {
    data object Generating : ImageGenerationEvent
    data object Done : ImageGenerationEvent
}
