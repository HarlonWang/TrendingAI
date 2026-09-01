package whl.trending.chat

import whl.trending.chat.host.ChatAiEvent
import whl.trending.chat.host.ChatAiOutcome
import whl.trending.chat.model.ChatError

/**
 * 失败终态事件：每次失败恰好一条 ai_completed，与 ai_requested 成对。
 * 配额触顶（付费意愿漏斗第一级）靠 `reason` 区分，不另开事件。
 */
internal fun failureEvent(error: ChatError, durationMs: Long? = null): ChatAiEvent.Completed =
    ChatAiEvent.Completed(
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
