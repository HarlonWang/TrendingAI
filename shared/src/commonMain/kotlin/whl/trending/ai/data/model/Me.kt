package whl.trending.ai.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MeResponse(
    val user: MeUser,
    /** 当前用户是否有生效的 Pro 权益（后端按 github_user_id 查 pro_entitlements） */
    val pro: Boolean = false,
)

/**
 * POST /api/pro/refresh 的响应。[reason] 仅在 [pro] 为 false 时出现，三种处置不同：
 * `github_not_linked` 钱付了但对不上、须引导关联；`not_sponsor` 正常不打扰；
 * `lookup_failed` 不能据此断言用户没赞助，保持沉默。
 */
@Serializable
data class ProRefreshResponse(val pro: Boolean = false, val reason: String? = null) {
    companion object {
        /** 见 [reason]，唯一需要客户端主动引导的分支。 */
        const val REASON_GITHUB_NOT_LINKED = "github_not_linked"
    }
}

@Serializable
data class MeUser(
    @SerialName("user_id") val userId: String,
    @SerialName("github_user_id") val githubUserId: Long? = null,
    @SerialName("github_login") val githubLogin: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val bio: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    /** 登录邮箱；可能为 null（无 email scope 的存量会话、或 GitHub 未公开邮箱） */
    val email: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

/** GET /api/quota 响应（查询顺带完成当日懒授予，返回即真实余额）。 */
@Serializable
data class QuotaResponse(
    val balance: Int,
    val dailyGrant: Int,
    /** 下次授予（UTC 零点重置）时间，ISO-8601 */
    val resetAt: String,
    val tier: String,
)
