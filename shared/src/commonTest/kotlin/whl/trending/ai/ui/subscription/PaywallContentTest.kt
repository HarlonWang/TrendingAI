package whl.trending.ai.ui.subscription

import whl.trending.ai.data.model.LocalizedText
import whl.trending.ai.data.model.PaywallCtaRemoteConfig
import whl.trending.ai.data.model.ProBenefitRow
import whl.trending.ai.data.model.ProPaywallRemoteConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PaywallContentTest {

    private fun t(zh: String? = null, en: String? = null) = LocalizedText(zh, en)

    @Test
    fun null_remote_yields_all_null_so_ui_falls_back_to_local_strings() {
        assertEquals(PaywallContent(), resolvePaywallContent(null, "zh"))
    }

    @Test
    fun zh_takes_zh_and_falls_back_to_en_per_key() {
        val remote = ProPaywallRemoteConfig(
            title = t("升级 Pro", "Upgrade"),
            subtitle = t(en = "Sub"),
            cta = PaywallCtaRemoteConfig(subscribe = t("订阅", "Subscribe")),
            refundNote = t(zh = "退款"),
        )
        val content = resolvePaywallContent(remote, "zh")
        assertEquals("升级 Pro", content.title)
        assertEquals("Sub", content.subtitle)
        assertEquals("订阅", content.ctaSubscribe)
        assertNull(content.ctaSignIn)
        assertEquals("退款", content.refundNote)
    }

    @Test
    fun en_never_reads_zh_and_missing_en_means_key_not_delivered() {
        val remote = ProPaywallRemoteConfig(title = t("升级 Pro", "Upgrade"), refundNote = t(zh = "退款"))
        val content = resolvePaywallContent(remote, "en")
        assertEquals("Upgrade", content.title)
        assertNull(content.refundNote)
    }

    @Test
    fun benefits_drop_rows_without_text_and_pass_unknown_icon_through() {
        val rows = listOf(
            ProBenefitRow("quota", t("额度", "Allowance")),
            ProBenefitRow("voice", t(zh = "语音")),
            ProBenefitRow("future", t(en = "X")),
            ProBenefitRow("broken", null),
        )
        assertEquals(
            listOf(BenefitItem("quota", "Allowance"), BenefitItem("future", "X")),
            resolveBenefits(rows, "en"),
        )
        assertEquals(3, resolveBenefits(rows, "zh").size)
    }
}
