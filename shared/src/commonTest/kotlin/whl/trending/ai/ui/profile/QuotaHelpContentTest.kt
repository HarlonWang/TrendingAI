package whl.trending.ai.ui.profile

import whl.trending.ai.data.model.LocalizedText
import whl.trending.ai.data.model.QuotaHelpRemoteConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QuotaHelpContentTest {

    private fun t(zh: String? = null, en: String? = null) = LocalizedText(zh, en)

    @Test
    fun null_remote_or_missing_title_yields_null_so_entry_is_hidden() {
        assertNull(resolveQuotaHelp(null, "zh"))
        assertNull(resolveQuotaHelp(QuotaHelpRemoteConfig(paragraphs = listOf(t(en = "x"))), "zh"))
    }

    @Test
    fun no_readable_paragraph_yields_null() {
        val remote = QuotaHelpRemoteConfig(title = t(en = "About"), paragraphs = listOf(t(zh = "只有中文")))
        assertNull(resolveQuotaHelp(remote, "en"))
    }

    @Test
    fun zh_falls_back_to_en_per_paragraph_and_skips_empty_rows() {
        val remote = QuotaHelpRemoteConfig(
            title = t(en = "About"),
            paragraphs = listOf(t("一", "One"), t(en = "Two"), t()),
        )
        assertEquals(QuotaHelpContent("About", listOf("一", "Two")), resolveQuotaHelp(remote, "zh"))
        assertEquals(QuotaHelpContent("About", listOf("One", "Two")), resolveQuotaHelp(remote, "en"))
    }
}
