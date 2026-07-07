package whl.trending.ai.data.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json

/** 模型目录的解码容错与 [resolveEffectiveChatModel] 生效判定单测。 */
class ChatModelOptionTest {

    private val free = ChatModelOption(id = "gpt-5.4", name = "GPT-5.4", minTier = "user")
    private val pro = ChatModelOption(id = "gpt-6", name = "GPT-6", minTier = "pro")
    private val catalog = listOf(free, pro)

    @Test
    fun entry_missing_name_and_minTier_decodes_with_defaults() {
        val parsed = Json.decodeFromString<ChatModelsResponse>("""{"models":[{"id":"m1"}]}""")
        val model = parsed.models.single()
        assertEquals("m1", model.name)
        assertEquals("user", model.minTier)
        assertEquals(false, model.proOnly)
    }

    @Test
    fun free_selection_passes_through() {
        assertEquals("gpt-5.4", resolveEffectiveChatModel(catalog, "gpt-5.4", isPro = false))
    }

    @Test
    fun pro_selection_kept_for_pro_user() {
        assertEquals("gpt-6", resolveEffectiveChatModel(catalog, "gpt-6", isPro = true))
    }

    @Test
    fun stale_pro_selection_falls_back_to_default_for_free_user() {
        assertEquals(DEFAULT_CHAT_MODEL, resolveEffectiveChatModel(catalog, "gpt-6", isPro = false))
    }

    @Test
    fun selection_absent_from_catalog_falls_back_to_default() {
        assertEquals(DEFAULT_CHAT_MODEL, resolveEffectiveChatModel(catalog, "removed-model", isPro = true))
    }

    @Test
    fun empty_catalog_passes_selection_through_unchanged() {
        assertEquals("gpt-6", resolveEffectiveChatModel(emptyList(), "gpt-6", isPro = false))
    }
}
