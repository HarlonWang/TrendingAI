package whl.trending.chat

import whl.trending.chat.ChatContext
import whl.trending.chat.model.ChatMessage
import whl.trending.chat.model.MessageKind
import whl.trending.chat.model.Role

/**
 * 「一键详细解读」chip 的可见性策略（纯函数）。
 *
 * 显示条件（同时满足）：GitHub 条目入口、README 长度达标（null 不显示，宁缺勿滥）、
 * 会话尚无成功的解读消息。满足即常驻（不同于介绍 chip 的「messages 为空才显示」），
 * 生成成功一次后隐藏；失败消息不算成功，可重新触发。
 */
object DetailSummaryPolicy {

    const val SOURCE_GITHUB = "github"

    /** 短 README 阈值，与服务端 detail-summary 的 GITHUB_MIN_README_CHARS 同源约定 */
    const val MIN_README_CHARS = 1500

    fun chipVisible(context: ChatContext?, messages: List<ChatMessage>): Boolean {
        if (context?.source != SOURCE_GITHUB || context.externalId.isNullOrBlank()) return false
        if ((context.readmeLength ?: 0) < MIN_README_CHARS) return false
        return messages.none {
            it.kind == MessageKind.DETAIL_SUMMARY &&
                it.role == Role.ASSISTANT &&
                it.error == null &&
                it.content.isNotBlank()
        }
    }
}
