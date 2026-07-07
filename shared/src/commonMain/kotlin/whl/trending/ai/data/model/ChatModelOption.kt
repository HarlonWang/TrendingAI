package whl.trending.ai.data.model

import kotlinx.serialization.Serializable

/** 免费默认模型（与后端 FREE_MODELS[0] 对齐）。选择器与请求兜底都用它。 */
const val DEFAULT_CHAT_MODEL = "gpt-5.4"

/**
 * 一个可选聊天模型（来自 `GET /api/chat/models`，后端从 OpenAI 动态取）。
 *
 * `name`/`minTier` 带缺省容错：目录由后端动态拼装，单条缺字段不该让整个目录解码失败、
 * 选择器整个消失（服务端仍按 tier 强制，缺省按免费展示最多被静默降级，不越权）。
 *
 * @param minTier 使用该模型所需的最低档位：`"user"`=免费可用；`"pro"`=需 Pro 解锁。
 */
@Serializable
data class ChatModelOption(
    val id: String,
    val name: String = id,
    val minTier: String = TIER_USER,
) {
    /** 是否 Pro 专属（免费用户看到但锁定） */
    val proOnly: Boolean get() = minTier == TIER_PRO

    companion object {
        /** minTier 的取值词汇，与后端 models.js 契约对齐；集中定义避免逻辑与测试各写各的字面量。 */
        const val TIER_USER = "user"
        const val TIER_PRO = "pro"
    }
}

@Serializable
data class ChatModelsResponse(val models: List<ChatModelOption> = emptyList())

/**
 * 计算实际生效的模型 id：持久化的选择对当前用户不可用（Pro 专属但非 Pro、或已不在目录中）时
 * 回落 [DEFAULT_CHAT_MODEL]；目录为空（尚未拉到）时原样透传，交服务端按 tier 强制。
 *
 * 选择器的自愈与 ChatApi 的发送共用本函数——「界面显示的」与「请求发出的」始终同一套判定。
 */
fun resolveEffectiveChatModel(models: List<ChatModelOption>, selectedId: String, isPro: Boolean): String {
    if (models.isEmpty()) return selectedId
    val sel = models.firstOrNull { it.id == selectedId }
    return if (sel == null || (sel.proOnly && !isPro)) DEFAULT_CHAT_MODEL else selectedId
}
