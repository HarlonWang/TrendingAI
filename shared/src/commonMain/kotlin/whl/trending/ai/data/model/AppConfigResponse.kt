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
)
