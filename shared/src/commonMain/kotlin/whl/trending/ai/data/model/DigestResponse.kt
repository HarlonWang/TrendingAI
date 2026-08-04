package whl.trending.ai.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GET /api/digest 响应。
 * 命中：{success:true, markdown, created_at}；
 * 未命中统一 404 {success:false, code:"digest_unavailable"}——服务端刻意不透出
 * insufficient/refused 等细分原因，客户端也只需三态 UI，不要依赖 code 做进一步分支。
 */
@Serializable
data class DigestResponse(
    val success: Boolean = false,
    val markdown: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    val code: String? = null,
)
