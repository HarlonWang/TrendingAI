package whl.trending.ai.data.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.json.Json

/** 模型目录的解码容错与 [resolveEffectiveChatModel] / [resolveDisplayedChatModel] 判定单测。 */
class ChatModelOptionTest {

    private val free = ChatModelOption(id = "gpt-5.6-luna", name = "GPT-5.6 Luna", minTier = ChatModelOption.TIER_USER)
    private val pro = ChatModelOption(id = "gpt-6", name = "GPT-6", minTier = ChatModelOption.TIER_PRO)
    private val catalog = ChatModelsResponse(models = listOf(free, pro), default = free.id)

    @Test
    fun entry_missing_name_and_minTier_decodes_with_defaults() {
        val parsed = Json.decodeFromString<ChatModelsResponse>("""{"default":"m1","models":[{"id":"m1"}]}""")
        val model = parsed.models.single()
        assertEquals("m1", model.name)
        assertEquals(ChatModelOption.TIER_USER, model.minTier)
        assertEquals(false, model.proOnly)
        assertEquals("m1", parsed.default)
    }

    /** 部署窗口内的旧缓存响应没有 default 字段：解码不炸，默认项判定为「不知道」 */
    @Test
    fun response_missing_default_decodes_and_yields_no_default() {
        val parsed = Json.decodeFromString<ChatModelsResponse>("""{"models":[{"id":"m1"}]}""")
        assertNull(catalogDefaultChatModel(parsed))
    }

    @Test
    fun free_selection_passes_through() {
        assertEquals("gpt-5.6-luna", resolveEffectiveChatModel(catalog.models, "gpt-5.6-luna", isPro = false))
    }

    @Test
    fun pro_selection_kept_for_pro_user() {
        assertEquals("gpt-6", resolveEffectiveChatModel(catalog.models, "gpt-6", isPro = true))
    }

    /** 核心解耦断言：没手选就不带 model，默认是谁由服务端定，客户端不复述模型 id。 */
    @Test
    fun unset_selection_sends_no_model() {
        assertNull(resolveEffectiveChatModel(catalog.models, FOLLOW_SERVER_DEFAULT, isPro = false))
        assertNull(resolveEffectiveChatModel(catalog.models, FOLLOW_SERVER_DEFAULT, isPro = true))
        assertNull(resolveEffectiveChatModel(emptyList(), FOLLOW_SERVER_DEFAULT, isPro = true))
    }

    @Test
    fun stale_pro_selection_sends_no_model_for_free_user() {
        assertNull(resolveEffectiveChatModel(catalog.models, "gpt-6", isPro = false))
    }

    @Test
    fun selection_absent_from_catalog_sends_no_model() {
        assertNull(resolveEffectiveChatModel(catalog.models, "removed-model", isPro = true))
    }

    @Test
    fun empty_catalog_passes_explicit_selection_through_unchanged() {
        assertEquals("gpt-6", resolveEffectiveChatModel(emptyList(), "gpt-6", isPro = false))
    }

    @Test
    fun displayed_model_is_declared_default_when_unset() {
        assertEquals(free, resolveDisplayedChatModel(catalog, FOLLOW_SERVER_DEFAULT, isPro = false))
        assertEquals(free, resolveDisplayedChatModel(catalog, FOLLOW_SERVER_DEFAULT, isPro = true))
    }

    /** 默认项由 default 字段声明，不靠排序猜：指向的项即使不排最前、目录里另有免费项也照选 */
    @Test
    fun displayed_model_follows_declared_default_not_ordering() {
        val otherFree = ChatModelOption(id = "gpt-5.6-terra", name = "GPT-5.6 Terra", minTier = ChatModelOption.TIER_USER)
        val declared = ChatModelsResponse(models = listOf(otherFree, free, pro), default = free.id)
        assertEquals(free, resolveDisplayedChatModel(declared, FOLLOW_SERVER_DEFAULT, isPro = false))
    }

    @Test
    fun displayed_model_never_shows_locked_entry_to_free_user() {
        assertEquals(free, resolveDisplayedChatModel(catalog, "gpt-6", isPro = false))
        assertEquals(pro, resolveDisplayedChatModel(catalog, "gpt-6", isPro = true))
    }

    @Test
    fun displayed_model_is_null_before_catalog_arrives() {
        assertNull(resolveDisplayedChatModel(ChatModelsResponse(), FOLLOW_SERVER_DEFAULT, isPro = false))
        assertNull(catalogDefaultChatModel(ChatModelsResponse()))
    }

    /** default 悬空（指向不在目录里的 id，契约破损）：不猜别的项，判定为「不知道」 */
    @Test
    fun dangling_default_yields_no_default() {
        val broken = ChatModelsResponse(models = listOf(free, pro), default = "ghost-model")
        assertNull(catalogDefaultChatModel(broken))
        assertNull(resolveDisplayedChatModel(broken, FOLLOW_SERVER_DEFAULT, isPro = false))
        // 有效的手选不受契约破损影响，照常显示
        assertEquals(pro, resolveDisplayedChatModel(broken, "gpt-6", isPro = true))
    }
}
