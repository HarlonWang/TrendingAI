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
 * POST /api/pro/refresh 的响应：后端用维护者 PAT 权威核对赞助后返回的最新 Pro 态。
 *
 * [reason] 仅在 [pro] 为 false 时出现，用来区分三种同样是「不是 Pro」但处置完全不同的情形：
 *   - `github_not_linked` 当前账户没关联 GitHub，**权益无从匹配**——赞助完回来落在这里
 *     就意味着「钱付了但对不上」，必须引导关联（见 [whl.trending.ai.core.ProSponsor]）
 *   - `not_sponsor`       已关联，查证后确实没在赞助——正常结果，不打扰
 *   - `lookup_failed`     GitHub 查询失败，**不能据此断言用户没赞助**，保持沉默等下次对账
 * 老后端不返回该字段，缺省 null 即退化为原先的纯布尔语义。
 */
@Serializable
data class ProRefreshResponse(val pro: Boolean = false, val reason: String? = null) {
    companion object {
        /** 未关联 GitHub：赞助后落在此原因 = 付了钱但权益对不上，是唯一需要主动引导的分支。 */
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
