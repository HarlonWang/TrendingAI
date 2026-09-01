package whl.trending.chat

import whl.trending.chat.host.ChatAiEvent
import whl.trending.chat.host.ChatAiKind
import whl.trending.chat.host.ChatAiOutcome
import whl.trending.chat.model.ChatError
import whl.trending.chat.model.MessageKind

internal fun MessageKind.toAiKind(): ChatAiKind = when (this) {
    MessageKind.CHAT -> ChatAiKind.CHAT
    MessageKind.DETAIL_SUMMARY -> ChatAiKind.DETAIL_SUMMARY
    MessageKind.DEEP_RESEARCH -> ChatAiKind.RESEARCH
}

/**
 * 失败终态事件：每次失败恰好一条 ai_completed，与 ai_requested 成对。配额触顶
 * （付费意愿漏斗第一级）与匿名解读登录闸（登录转化信号）都靠 `reason` 区分。
 */
internal fun failureEvent(kind: MessageKind, error: ChatError, durationMs: Long? = null): ChatAiEvent.Completed =
    ChatAiEvent.Completed(
        kind = kind.toAiKind(),
        outcome = if (error.code == ChatError.CODE_STREAM_INTERRUPTED) {
            ChatAiOutcome.INTERRUPTED
        } else {
            ChatAiOutcome.ERROR
        },
        durationMs = durationMs,
        reason = error.code ?: error.category.name.lowercase(),
        tier = if (error.code == ChatError.CODE_QUOTA_DEVICE) {
            error.tier ?: ChatError.TIER_ANONYMOUS
        } else {
            null
        },
    )
