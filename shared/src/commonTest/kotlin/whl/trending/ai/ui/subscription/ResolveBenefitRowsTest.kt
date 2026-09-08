package whl.trending.ai.ui.subscription

import whl.trending.ai.data.model.LocalizedText
import whl.trending.ai.data.model.ProBenefitRow
import kotlin.test.Test
import kotlin.test.assertEquals

class ResolveBenefitRowsTest {

    private fun row(label: LocalizedText, free: LocalizedText, pro: LocalizedText) = ProBenefitRow(label, free, pro)
    private fun t(zh: String? = null, en: String? = null) = LocalizedText(zh, en)

    @Test
    fun zh_takes_zh_and_falls_back_to_en_per_cell() {
        val rows = listOf(row(t("额度", "Allowance"), t(en = "Some"), t("更多", "More")))
        assertEquals(
            listOf(BenefitRowText("额度", "Some", "更多")),
            resolveBenefitRows(rows, "zh"),
        )
    }

    @Test
    fun en_never_reads_zh() {
        val rows = listOf(row(t("额度", "Allowance"), t("少量", "Some"), t("更多", "More")))
        assertEquals(
            listOf(BenefitRowText("Allowance", "Some", "More")),
            resolveBenefitRows(rows, "en"),
        )
    }

    @Test
    fun row_missing_en_is_dropped_whole_not_half_rendered() {
        val rows = listOf(
            row(t("额度", "Allowance"), t(zh = "少量"), t("更多", "More")),
            row(t("模型", "Models"), t("默认", "Default"), t("全部", "All")),
        )
        assertEquals(listOf(BenefitRowText("Models", "Default", "All")), resolveBenefitRows(rows, "en"))
        // 同一份数据在 zh 下两行齐全，缺 en 只影响英文用户
        assertEquals(2, resolveBenefitRows(rows, "zh").size)
    }
}
