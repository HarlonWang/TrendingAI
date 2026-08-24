package whl.trending.ai.data.model

import kotlinx.serialization.Serializable

/**
 * 「跟随服务端默认」的哨兵值：请求不带 model 字段，由服务端按 tier 决定。
 * 语义是用户意图而非「没设置过」（手选默认项存的同样是它）；客户端刻意不硬编码默认模型 id，
 * 否则后端换默认模型对存量版本无效。
 */
const val FOLLOW_SERVER_DEFAULT = ""

/**
 * 一个可选聊天模型（`GET /api/chat/models`）。
 * `name`/`minTier` 带缺省容错：单条缺字段不该让整个目录解码失败（服务端仍按 tier 强制，不越权）。
 */
@Serializable
data class ChatModelOption(
    val id: String,
    val name: String = id,
    val minTier: String = TIER_USER,
) {
    val proOnly: Boolean get() = minTier == TIER_PRO

    companion object {
        /** minTier 取值词汇，与后端 models.js 契约对齐。 */
        const val TIER_USER = "user"
        const val TIER_PRO = "pro"
    }
}

/**
 * 模型目录（`GET /api/chat/models` 的完整响应）。
 * @param default 未手选时服务端实际使用的模型 id；缺省 `""` 仅为解码容错，解析不到默认项时展示各自缺省、不按排序猜。
 */
@Serializable
data class ChatModelsResponse(
    val models: List<ChatModelOption> = emptyList(),
    val default: String = "",
)

/**
 * 计算请求该带的模型 id，`null` = 不带 model 字段、由服务端决定默认。
 * 目录为空（尚未拉到）时手选值原样透传，交服务端按 tier 强制；选择器自愈与 ChatApi 发送共用本函数。
 */
fun resolveEffectiveChatModel(models: List<ChatModelOption>, selectedId: String, isPro: Boolean): String? {
    if (selectedId == FOLLOW_SERVER_DEFAULT) return null
    if (models.isEmpty()) return selectedId
    val sel = models.firstOrNull { it.id == selectedId }
    return if (sel == null || (sel.proOnly && !isPro)) null else selectedId
}

/** 目录声明的默认模型条目；拿不到时为 null——不猜、不回落排序。 */
fun catalogDefaultChatModel(catalog: ChatModelsResponse): ChatModelOption? =
    catalog.models.firstOrNull { it.id == catalog.default }

/** 当前实际生效的模型条目；未手选 / 手选失效时取 [catalogDefaultChatModel]，与服务端兜底一致。 */
fun resolveDisplayedChatModel(catalog: ChatModelsResponse, selectedId: String, isPro: Boolean): ChatModelOption? {
    val effective = resolveEffectiveChatModel(catalog.models, selectedId, isPro)
    return catalog.models.firstOrNull { it.id == effective } ?: catalogDefaultChatModel(catalog)
}
