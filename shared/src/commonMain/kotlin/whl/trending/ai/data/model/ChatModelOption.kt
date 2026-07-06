package whl.trending.ai.data.model

import kotlinx.serialization.Serializable

/** 免费默认模型（与后端 FREE_MODELS[0] 对齐）。选择器与请求兜底都用它。 */
const val DEFAULT_CHAT_MODEL = "gpt-5.4"

/**
 * 一个可选聊天模型（来自 `GET /api/chat/models`，后端从 OpenAI 动态取）。
 *
 * @param minTier 使用该模型所需的最低档位：`"user"`=免费可用；`"pro"`=需 Pro 解锁。
 */
@Serializable
data class ChatModelOption(
    val id: String,
    val name: String,
    val minTier: String,
) {
    /** 是否 Pro 专属（免费用户看到但锁定） */
    val proOnly: Boolean get() = minTier == "pro"
}

@Serializable
data class ChatModelsResponse(val models: List<ChatModelOption> = emptyList())
