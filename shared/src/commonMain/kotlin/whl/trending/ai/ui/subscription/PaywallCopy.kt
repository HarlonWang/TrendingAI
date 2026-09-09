package whl.trending.ai.ui.subscription

import whl.trending.ai.data.model.LocalizedText
import whl.trending.ai.data.model.ProBenefitRow
import whl.trending.ai.data.model.ProPaywallRemoteConfig

/** 权益清单一行，已按 UI 语言选好文案；[icon] 是服务端给的 key，由 UI 映射成图标 */
data class BenefitItem(val icon: String?, val text: String)

/** 订阅页文案，已按 UI 语言选好；字段为 null 表示服务端未下发，UI 用本地默认 */
data class PaywallCopy(
    val title: String? = null,
    val subtitle: String? = null,
    val benefits: List<BenefitItem> = emptyList(),
    val ctaSubscribe: String? = null,
    val ctaSignIn: String? = null,
    val ctaViewPrice: String? = null,
    val refundNote: String? = null,
    val alreadyPro: String? = null,
    val checkoutFailed: String? = null,
)

internal fun resolvePaywallCopy(remote: ProPaywallRemoteConfig?, lang: String): PaywallCopy {
    if (remote == null) return PaywallCopy()
    fun LocalizedText?.pick(): String? = this?.forLang(lang)
    return PaywallCopy(
        title = remote.title.pick(),
        subtitle = remote.subtitle.pick(),
        benefits = resolveBenefits(remote.benefits, lang),
        ctaSubscribe = remote.cta?.subscribe.pick(),
        ctaSignIn = remote.cta?.signIn.pick(),
        ctaViewPrice = remote.cta?.viewPrice.pick(),
        refundNote = remote.refundNote.pick(),
        alreadyPro = remote.alreadyPro.pick(),
        checkoutFailed = remote.checkoutFailed.pick(),
    )
}

/** 按语言取文案；取不到的行跳过 */
internal fun resolveBenefits(rows: List<ProBenefitRow>, lang: String): List<BenefitItem> =
    rows.mapNotNull { row -> row.text?.forLang(lang)?.let { BenefitItem(row.icon, it) } }
