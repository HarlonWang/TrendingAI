package whl.trending.ai.data.model

import kotlinx.serialization.Serializable

/**
 * 「未手选模型」的哨兵值：请求不带 model 字段，由服务端按 tier 决定默认。
 *
 * 客户端刻意不再硬编码默认模型 id。后端 `resolveModel(tier, undefined)` 两档都回落
 * `DEFAULT_MODEL`，所以「不传」就是「跟随后端默认」——后端换默认模型不必跟着发版。
 * 曾经硬编码是有代价的：免费档被后端白名单拍平看不出问题，Pro 档 `isOfferedModel` 放行，
 * 于是未手选的 Pro 用户实际被钉在客户端常量上，后端调档对存量版本无效（0aac277）。
 */
const val CHAT_MODEL_UNSET = ""

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

/**
 * 模型目录（`GET /api/chat/models` 的完整响应，也是客户端内的流通类型）。
 *
 * @param default 未手选时服务端实际使用的模型 id（后端 DEFAULT_MODEL，恒指向 [models] 中的免费项）。
 *   显式契约字段——客户端不硬编码默认模型 id，也不按「免费项排最前」的排序习惯去猜。
 *   缺省 `""` 仅为解码容错（部署窗口内的旧缓存响应）：解析不到默认项时相关展示各自缺省，不猜。
 */
@Serializable
data class ChatModelsResponse(
    val models: List<ChatModelOption> = emptyList(),
    val default: String = "",
)

/**
 * 计算请求该带的模型 id，`null` = 不带 model 字段、由服务端决定默认。
 *
 * 返回 null 的三种情形：未手选（[CHAT_MODEL_UNSET]）、选择已不在目录中、选择是 Pro 专属但当前非 Pro。
 * 目录为空（尚未拉到）时手选值原样透传，交服务端按 tier 强制。
 *
 * 选择器的自愈与 ChatApi 的发送共用本函数——「界面显示的」与「请求发出的」始终同一套判定。
 */
fun resolveEffectiveChatModel(models: List<ChatModelOption>, selectedId: String, isPro: Boolean): String? {
    if (selectedId == CHAT_MODEL_UNSET) return null
    if (models.isEmpty()) return selectedId
    val sel = models.firstOrNull { it.id == selectedId }
    return if (sel == null || (sel.proOnly && !isPro)) null else selectedId
}

/**
 * 目录声明的默认模型条目：`default` 字段指向的那一项。
 * 目录尚未拉到、或字段缺失/悬空（不该发生，后端有测试钉住）时为 null——不猜，不回落排序。
 */
fun catalogDefaultChatModel(catalog: ChatModelsResponse): ChatModelOption? =
    catalog.models.firstOrNull { it.id == catalog.default }

/**
 * 当前实际生效的模型条目，供展示与「哪个模型答的」留痕用。
 * 未手选 / 手选失效时取 [catalogDefaultChatModel]，与服务端的兜底一致。
 */
fun resolveDisplayedChatModel(catalog: ChatModelsResponse, selectedId: String, isPro: Boolean): ChatModelOption? {
    val effective = resolveEffectiveChatModel(catalog.models, selectedId, isPro)
    return catalog.models.firstOrNull { it.id == effective } ?: catalogDefaultChatModel(catalog)
}
