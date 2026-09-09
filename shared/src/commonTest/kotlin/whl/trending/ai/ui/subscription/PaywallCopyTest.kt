package whl.trending.ai.ui.subscription

import whl.trending.ai.data.model.LocalizedText
import whl.trending.ai.data.model.PaywallCtaRemoteConfig
import whl.trending.ai.data.model.ProBenefitRow
import whl.trending.ai.data.model.ProPaywallRemoteConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PaywallCopyTest {

    private fun t(zh: String? = null, en: String? = null) = LocalizedText(zh, en)

    @Test
    fun null_remote_yields_all_null_so_ui_falls_back_to_local_strings() {
        assertEquals(PaywallCopy(), resolvePaywallCopy(null, "zh"))
    }

    @Test
    fun zh_takes_zh_and_falls_back_to_en_per_key() {
        val remote = ProPaywallRemoteConfig(
            title = t("升级 Pro", "Upgrade"),
            subtitle = t(en = "Sub"),
            cta = PaywallCtaRemoteConfig(subscribe = t("订阅", "Subscribe")),
            refundNote = t(zh = "退款"),
        )
        val copy = resolvePaywallCopy(remote, "zh")
        assertEquals("升级 Pro", copy.title)
        assertEquals("Sub", copy.subtitle)
        assertEquals("订阅", copy.ctaSubscribe)
        assertNull(copy.ctaSignIn)
        assertEquals("退款", copy.refundNote)
    }

    @Test
    fun en_never_reads_zh_and_missing_en_means_key_not_delivered() {
        val remote = ProPaywallRemoteConfig(title = t("升级 Pro", "Upgrade"), refundNote = t(zh = "退款"))
        val copy = resolvePaywallCopy(remote, "en")
        assertEquals("Upgrade", copy.title)
        assertNull(copy.refundNote)
    }

    @Test
    fun benefits_drop_rows_without_text_and_pass_unknown_icon_through() {
        val rows = listOf(
            ProBenefitRow("quota", t("额度", "Allowance")),
            ProBenefitRow("voice", t(zh = "语音")),
            ProBenefitRow("future", t(en = "X")),
        )
        assertEquals(
            listOf(BenefitItem("quota", "Allowance"), BenefitItem("future", "X")),
            resolveBenefits(rows, "en"),
        )
        assertEquals(3, resolveBenefits(rows, "zh").size)
    }
}
