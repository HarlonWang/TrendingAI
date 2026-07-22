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
    /** 登录邮箱；存量会话的 token 无 email scope 时为 null（重登后补齐），GitHub 用户亦可能无 */
    val email: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

/**
 * GET /api/quota 响应：credits 余额可见性（查询顺带完成当日懒授予，返回即真实余额）。
 * 消耗参考费率：普通对话 1 / 联网搜索 3 / Deep Research 10 credits。
 */
@Serializable
data class QuotaResponse(
    /** 当前 credits 余额 */
    val balance: Int,
    /** 当前档位的每日授予额（anonymous 5 / user 10 / pro 100） */
    val dailyGrant: Int,
    /** 下次授予（UTC 零点重置）时间，ISO-8601 */
    val resetAt: String,
    /** 档位：anonymous / user / pro */
    val tier: String,
)
