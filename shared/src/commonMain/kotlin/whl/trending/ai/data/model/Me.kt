package whl.trending.ai.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MeResponse(
    val user: MeUser,
    /** 当前用户是否有生效的 Pro 权益（后端按 github_user_id 查 pro_entitlements） */
    val pro: Boolean = false,
)

/** POST /api/pro/refresh 的响应：后端用维护者 PAT 权威核对赞助后返回的最新 Pro 态。 */
@Serializable
data class ProRefreshResponse(val pro: Boolean = false)

@Serializable
data class MeUser(
    @SerialName("user_id") val userId: String,
    @SerialName("github_user_id") val githubUserId: Long? = null,
    @SerialName("github_login") val githubLogin: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val bio: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)
