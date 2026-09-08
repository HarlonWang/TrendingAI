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
    @SerialName("pro_benefits") val proBenefits: ProBenefitsRemoteConfig? = null,
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

/** Pro 权益清单（服务端单源下发，行顺序即展示顺序；见后端 docs/pro-benefits.md） */
@Serializable
data class ProBenefitsRemoteConfig(
    val rows: List<ProBenefitRow> = emptyList(),
)

/** [icon] 是客户端图标映射表的 key，认不出时用通用图标，新行在旧客户端照常显示 */
@Serializable
data class ProBenefitRow(
    val icon: String? = null,
    val text: LocalizedText,
)

@Serializable
data class LocalizedText(
    val zh: String? = null,
    val en: String? = null,
) {
    /** 按 UI 语言取值：zh 缺回落 en；en 也缺返回 null，调用方整行跳过 */
    fun forLang(lang: String): String? = if (lang == "zh") zh ?: en else en
}
