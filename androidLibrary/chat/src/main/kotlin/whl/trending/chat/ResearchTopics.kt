package whl.trending.chat

import whl.trending.ai.chat.ChatContext

/**
 * research 主题的条目锚点拼装（纯函数）。
 *
 * 条目会话里发起的 research 在主题尾部附上标题 + 链接，让联网检索锁定正确项目
 * （同名项目/常见词主题极易漂移）。落库与气泡展示的仍是用户原文；拼装收口在
 * 发送前最后一步（[whl.trending.chat.ChatViewModel] 的 startResearch），发送与
 * 重试两条路径都传原文、各自重新拼装，保证同构。
 */
object ResearchTopics {

    /** 与服务端 research 接口 MAX_TOPIC_LEN 同源约定 */
    const val MAX_TOPIC_LEN = 500

    fun compose(text: String, context: ChatContext?): String {
        val anchor = listOfNotNull(
            context?.title?.takeIf { it.isNotBlank() },
            context?.sourceUrl?.takeIf { it.isNotBlank() },
        ).joinToString(" ")
        if (anchor.isBlank()) return text
        val composed = "$text\n\nTarget: $anchor"
        // 附加后超上限则回退原文：宁可丢锚点，不截用户内容、不让请求被 400 拒掉
        return if (composed.length <= MAX_TOPIC_LEN) composed else text
    }
}
