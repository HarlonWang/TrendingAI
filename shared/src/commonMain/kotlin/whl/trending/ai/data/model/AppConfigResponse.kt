package whl.trending.ai.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GET /api/app-config 响应。字段全部可空以便服务端渐进上线；
 * 未来可扩展 latest_version / download_url 等字段（客户端 ignoreUnknownKeys）。
 */
@Serializable
data class AppConfigResponse(
    @SerialName("min_version") val minVersion: String? = null,
    @SerialName("chat_images") val chatImages: ChatImagesRemoteConfig? = null,
    @SerialName("chat_voice") val chatVoice: ChatVoiceRemoteConfig? = null,
    @SerialName("pro_paywall") val proPaywall: ProPaywallRemoteConfig? = null,
    @SerialName("quota_help") val quotaHelp: QuotaHelpRemoteConfig? = null,
)

/** chat 图片参数（服务端 KV 单源下发，与服务端校验闸同值；见后端 lib/chat-images.js） */
@Serializable
data class ChatImagesRemoteConfig(
    @SerialName("max_count") val maxCount: Int? = null,
    @SerialName("per_image_jpeg_kb") val perImageJpegKb: Int? = null,
)

/** chat 语音录入参数（服务端 KV 单源下发，与服务端校验闸同值；见后端 lib/chat-voice.js） */
@Serializable
data class ChatVoiceRemoteConfig(
    @SerialName("max_duration_ms") val maxDurationMs: Int? = null,
)

/**
 * 订阅页文案（服务端单源下发；见后端 docs/pro-paywall.md）。字段全部可空：
 * 缺哪个键客户端就用本地默认，方案标签与价格不在此列。
 */
@Serializable
data class ProPaywallRemoteConfig(
    val title: LocalizedText? = null,
    val subtitle: LocalizedText? = null,
    val benefits: List<ProBenefitRow> = emptyList(),
    val cta: PaywallCtaRemoteConfig? = null,
    @SerialName("refund_note") val refundNote: LocalizedText? = null,
    @SerialName("already_pro") val alreadyPro: LocalizedText? = null,
    @SerialName("checkout_failed") val checkoutFailed: LocalizedText? = null,
)

@Serializable
data class PaywallCtaRemoteConfig(
    val subscribe: LocalizedText? = null,
    @SerialName("sign_in") val signIn: LocalizedText? = null,
    @SerialName("view_price") val viewPrice: LocalizedText? = null,
)

/** [icon] 是客户端图标映射表的 key，认不出时用通用图标，新行在旧客户端照常显示 */
@Serializable
data class ProBenefitRow(
    val icon: String? = null,
    // 可空：一行漏了 text 只丢这一行，不能让整个 app-config（含强更配置）解码失败
    val text: LocalizedText? = null,
)

/**
 * 账户页额度说明（服务端单源下发；见后端 docs/quota-help.md）。
 * 没有本地默认：说明全是数字，落后的默认即错误信息，未下发就不显示入口。
 */
@Serializable
data class QuotaHelpRemoteConfig(
    val title: LocalizedText? = null,
    val paragraphs: List<LocalizedText> = emptyList(),
)

@Serializable
data class LocalizedText(
    val zh: String? = null,
    val en: String? = null,
) {
    /** 按 UI 语言取值：zh 缺回落 en；en 也缺返回 null，调用方整行跳过 */
    fun forLang(lang: String): String? = if (lang == "zh") zh ?: en else en
}
