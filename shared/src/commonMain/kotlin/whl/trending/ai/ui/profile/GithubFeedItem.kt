package whl.trending.ai.ui.profile

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import whl.trending.ai.auth.FollowingInfo
import whl.trending.ai.data.remote.GithubEventDto

enum class GithubFeedKind {
    STARRED, FORKED, CREATED_REPO, CREATED_BRANCH, CREATED_TAG, RELEASED,
    PUSHED, PR_OPENED, PR_MERGED, PR_CLOSED,
    ISSUE_OPENED, ISSUE_CLOSED, ISSUE_COMMENTED, MADE_PUBLIC,
    STARRED_YOUR_REPO, FORKED_YOUR_REPO,
    OTHER,
}

/**
 * 归一化后的 feed 条目：kind + primary（分支名/tag/编号/提交数/类型名，按 kind 而定），
 * 文案在 UI 层用 stringResource 按 kind 组装（i18n）。
 * @Serializable 供 Profile 上次数据缓存（ProfileCache）序列化落盘。
 */
@Serializable
data class GithubFeedItem(
    val id: String,
    val actorLogin: String,
    val actorAvatarUrl: String?,
    val repoName: String,
    val kind: GithubFeedKind,
    val primary: String?,
    val createdAt: String,
    val targetUrl: String,
)

fun GithubEventDto.toFeedItem(): GithubFeedItem {
    val p = payload as? JsonObject ?: (payload?.let { runCatching { it.jsonObject }.getOrNull() })
    val repoUrl = "https://github.com/${repo.name}"

    fun str(obj: JsonObject?, key: String): String? =
        obj?.get(key)?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }

    fun obj(parent: JsonObject?, key: String): JsonObject? =
        parent?.get(key)?.let { runCatching { it.jsonObject }.getOrNull() }

    var kind = GithubFeedKind.OTHER
    var primary: String? = type.removeSuffix("Event")
    var targetUrl = repoUrl

    when (type) {
        "WatchEvent" -> { kind = GithubFeedKind.STARRED; primary = null }
        "ForkEvent" -> { kind = GithubFeedKind.FORKED; primary = null }
        "PublicEvent" -> { kind = GithubFeedKind.MADE_PUBLIC; primary = null }
        "CreateEvent" -> when (str(p, "ref_type")) {
            "repository" -> { kind = GithubFeedKind.CREATED_REPO; primary = null }
            "branch" -> { kind = GithubFeedKind.CREATED_BRANCH; primary = str(p, "ref") }
            "tag" -> { kind = GithubFeedKind.CREATED_TAG; primary = str(p, "ref") }
        }
        "ReleaseEvent" -> {
            val release = obj(p, "release")
            kind = GithubFeedKind.RELEASED
            primary = str(release, "tag_name")
            str(release, "html_url")?.let { targetUrl = it }
        }
        "PushEvent" -> {
            kind = GithubFeedKind.PUSHED
            primary = (p?.get("size")?.jsonPrimitive?.intOrNull ?: 1).toString()
        }
        "PullRequestEvent" -> {
            val pr = obj(p, "pull_request")
            val number = p?.get("number")?.jsonPrimitive?.intOrNull
            if (pr != null && number != null) {
                kind = when {
                    str(p, "action") == "opened" -> GithubFeedKind.PR_OPENED
                    str(p, "action") == "merged" -> GithubFeedKind.PR_MERGED
                    str(p, "action") == "closed" &&
                        pr["merged"]?.jsonPrimitive?.booleanOrNull == true -> GithubFeedKind.PR_MERGED
                    str(p, "action") == "closed" -> GithubFeedKind.PR_CLOSED
                    else -> GithubFeedKind.OTHER
                }
                if (kind != GithubFeedKind.OTHER) {
                    primary = number.toString()
                    str(pr, "html_url")?.let { targetUrl = it }
                }
            }
        }
        "IssuesEvent" -> {
            val issue = obj(p, "issue")
            val number = issue?.get("number")?.jsonPrimitive?.intOrNull
            if (issue != null && number != null) {
                kind = when (str(p, "action")) {
                    "opened" -> GithubFeedKind.ISSUE_OPENED
                    "closed" -> GithubFeedKind.ISSUE_CLOSED
                    else -> GithubFeedKind.OTHER
                }
                if (kind != GithubFeedKind.OTHER) {
                    primary = number.toString()
                    str(issue, "html_url")?.let { targetUrl = it }
                }
            }
        }
        "IssueCommentEvent" -> {
            val issue = obj(p, "issue")
            val number = issue?.get("number")?.jsonPrimitive?.intOrNull
            if (number != null) {
                kind = GithubFeedKind.ISSUE_COMMENTED
                primary = number.toString()
                targetUrl = str(obj(p, "comment"), "html_url")
                    ?: str(issue, "html_url")
                    ?: repoUrl
            }
        }
    }

    return GithubFeedItem(
        id = id,
        actorLogin = actor.login,
        actorAvatarUrl = actor.avatarUrl,
        repoName = repo.name,
        kind = kind,
        primary = primary,
        createdAt = createdAt,
        targetUrl = targetUrl,
    )
}

/** 精选档保留的高信号事件（与 GitHub 网页 Dashboard 风格对齐；issue/push/review 等不进） */
val HighlightFeedKinds: Set<GithubFeedKind> = setOf(
    GithubFeedKind.STARRED,
    GithubFeedKind.FORKED,
    GithubFeedKind.RELEASED,
    GithubFeedKind.CREATED_REPO,
    GithubFeedKind.MADE_PUBLIC,
    GithubFeedKind.PR_OPENED,
    GithubFeedKind.PR_MERGED,
)

/** 组织产出型事件：关注的组织仓库上只保留这些（路人 star/fork 不算产出） */
val OrgOutputFeedKinds: Set<GithubFeedKind> = setOf(
    GithubFeedKind.RELEASED,
    GithubFeedKind.CREATED_REPO,
    GithubFeedKind.MADE_PUBLIC,
)

fun GithubFeedItem.isBot(): Boolean = actorLogin.endsWith("[bot]")

/**
 * 精选档判定：
 * 规则1 关注的人的高信号动作（滤 bot）；规则2 关注的组织的产出（不滤 bot）。
 * following 为 null（拉取失败降级）时退回"仅按类型 + 非 bot"的旧行为。
 */
fun GithubFeedItem.isHighlight(following: FollowingInfo?): Boolean {
    if (following == null) return kind in HighlightFeedKinds && !isBot()
    val actorLower = actorLogin.lowercase()
    val repoOwnerLower = repoName.substringBefore('/').lowercase()
    val byFollowedUser = actorLower in following.users &&
        kind in HighlightFeedKinds && !isBot()
    val byFollowedOrgOutput = repoOwnerLower in following.orgs &&
        kind in OrgOutputFeedKinds
    return byFollowedUser || byFollowedOrgOutput
}
