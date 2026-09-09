package whl.trending.ai.ui.profile

import whl.trending.ai.data.model.QuotaHelpRemoteConfig

/** 额度说明，已按 UI 语言选好；[paragraphs] 非空才有东西可弹 */
data class QuotaHelpContent(val title: String, val paragraphs: List<String>)

/** 标题或全部段落取不到即视为未下发，返回 null；取不到文案的段落跳过 */
internal fun resolveQuotaHelp(remote: QuotaHelpRemoteConfig?, lang: String): QuotaHelpContent? {
    val title = remote?.title?.forLang(lang) ?: return null
    val paragraphs = remote.paragraphs.mapNotNull { it.forLang(lang) }
    if (paragraphs.isEmpty()) return null
    return QuotaHelpContent(title, paragraphs)
}
