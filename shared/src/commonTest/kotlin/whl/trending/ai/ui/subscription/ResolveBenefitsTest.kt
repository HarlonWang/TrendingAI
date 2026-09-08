package whl.trending.ai.ui.subscription

import whl.trending.ai.data.model.LocalizedText
import whl.trending.ai.data.model.ProBenefitRow
import kotlin.test.Test
import kotlin.test.assertEquals

class ResolveBenefitsTest {

    private fun row(icon: String?, zh: String? = null, en: String? = null) = ProBenefitRow(icon, LocalizedText(zh, en))

    @Test
    fun zh_takes_zh_and_falls_back_to_en() {
        val rows = listOf(row("quota", "额度", "Allowance"), row("models", en = "Models"))
        assertEquals(
            listOf(BenefitItem("quota", "额度"), BenefitItem("models", "Models")),
            resolveBenefits(rows, "zh"),
        )
    }

    @Test
    fun en_never_reads_zh_and_drops_rows_without_en() {
        val rows = listOf(row("quota", "额度", "Allowance"), row("voice", zh = "语音"))
        assertEquals(listOf(BenefitItem("quota", "Allowance")), resolveBenefits(rows, "en"))
    }

    @Test
    fun unknown_icon_key_is_passed_through_for_ui_fallback() {
        assertEquals(BenefitItem("future", "X"), resolveBenefits(listOf(row("future", en = "X")), "en").single())
    }
}
