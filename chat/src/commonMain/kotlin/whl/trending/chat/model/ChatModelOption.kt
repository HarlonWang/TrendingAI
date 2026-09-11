package whl.trending.chat.model

import kotlinx.serialization.Serializable

/**
 * 「跟随服务端默认」的哨兵值：请求不带 model 字段，由服务端按 tier 决定。
 * 语义是用户意图而非「没设置过」（手选默认项存的同样是它）；客户端刻意不硬编码默认模型 id，
 * 否则后端换默认模型对存量版本无效。
 */
const val FOLLOW_SERVER_DEFAULT = ""

/**
 * 模型能力（契约 `caps`）。输入能力缺省全开：旧服务端不下发时行为不变；
 * 输出能力 [imageOut] 缺省关——旧服务端不下发时不能凭空亮出一个打过去必 400 的入口。
 * 服务端在请求时另有真闸（`images_unsupported` / `search_unsupported` / `image_unsupported`），这里只管入口。
 */
@Serializable
data class ChatModelCaps(
    val images: Boolean = true,
    val search: Boolean = true,
    val imageOut: Boolean = false,
)

/**
 * 一个可选聊天模型（`GET /api/chat/models`）。
 * `name`/`minTier`/`provider`/`caps` 带缺省容错：单条缺字段不该让整个目录解码失败（服务端仍按 tier 强制，不越权）。
 */
@Serializable
data class ChatModelOption(
    val id: String,
    val name: String = id,
    val minTier: String = TIER_USER,
    val provider: String = PROVIDER_DEFAULT,
    val providerName: String = PROVIDER_DEFAULT_NAME,
    val caps: ChatModelCaps = ChatModelCaps(),
) {
    val proOnly: Boolean get() = minTier == TIER_PRO

    companion object {
        /** minTier 取值词汇，与后端 models.js 契约对齐。 */
        const val TIER_USER = "user"
        const val TIER_PRO = "pro"

        /** 旧服务端不下发 provider 字段时的缺省：多厂商上线前目录只有 OpenAI */
        const val PROVIDER_DEFAULT = "openai"
        const val PROVIDER_DEFAULT_NAME = "OpenAI"
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

/** 当前生效模型的能力位；目录未到或解析不出时按全开处理（与缺省一致，不多藏入口）。 */
fun effectiveChatModelCaps(catalog: ChatModelsResponse, selectedId: String, isPro: Boolean): ChatModelCaps =
    resolveDisplayedChatModel(catalog, selectedId, isPro)?.caps ?: ChatModelCaps()

/** 目录里出现的厂商展示名（按目录顺序去重），供出处标注拼接；目录未到时为空。 */
fun catalogProviderNames(catalog: ChatModelsResponse): List<String> =
    catalog.models.map { it.providerName }.distinct()
