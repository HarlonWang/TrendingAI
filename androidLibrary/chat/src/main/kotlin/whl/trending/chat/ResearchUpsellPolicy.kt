package whl.trending.chat

import whl.trending.chat.model.ChatMessage
import whl.trending.chat.model.MessageKind
import whl.trending.chat.model.Role

/**
 * 解读卡尾部「深度调研此项目」升级入口的可见性策略（纯函数）。
 *
 * 挂在最后一条成功解读消息的尾部：解读（便宜、秒级）是漏斗上层，research
 * （费率最高、分钟级）是升级动作。会话里一旦出现任何 research 消息——包括
 * 在途占位与失败条——入口即隐藏：失败已有自己的重试路径，这里再放入口
 * 等于诱导重复扣费。
 */
object ResearchUpsellPolicy {

    /** 返回应挂升级入口的消息 id；无处可挂则 null */
    fun upsellMessageId(messages: List<ChatMessage>): Long? {
        if (messages.any { it.kind == MessageKind.DEEP_RESEARCH }) return null
        return messages.lastOrNull {
            it.kind == MessageKind.DETAIL_SUMMARY &&
                it.role == Role.ASSISTANT &&
                it.error == null &&
                it.content.isNotBlank()
        }?.id
    }
}
