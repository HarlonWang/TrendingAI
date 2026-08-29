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
)

/** chat 图片参数（服务端 KV 单源下发，与服务端校验闸同值；见后端 lib/chat-images.js） */
@Serializable
data class ChatImagesRemoteConfig(
    @SerialName("max_count") val maxCount: Int? = null,
    @SerialName("per_image_jpeg_kb") val perImageJpegKb: Int? = null,
)
