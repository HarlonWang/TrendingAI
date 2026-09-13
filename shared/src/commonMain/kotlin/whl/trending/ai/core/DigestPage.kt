package whl.trending.ai.core

import whl.trending.ai.core.analytics.Screen
import whl.trending.ai.data.model.FavoriteItem
import whl.trending.ai.data.model.FeedItem
import whl.trending.ai.data.model.PickItem
import whl.trending.ai.data.model.TrendingRepo

/**
 * 解读页路由参数（Nav3 backStack key），三源共用：HN / GitHub / Product Hunt。
 *
 * 列表已有的数据全部带进来：头部（标题/热度）即时渲染不等网络；
 * 收藏落库沿用与列表完全相同的字段（url 主键 + source/externalId 云同步键），
 * 保证解读页收藏与列表收藏是同一条记录。
 */
data class DigestPage(
    val source: String,
    val externalId: String,
    val title: String,
    /** 原文链接：HN 文章 / GitHub 仓库页 / PH 产品官网（自建帖时与 [discussionUrl] 相同） */
    val url: String,
    /** 讨论/来源页：HN 讨论区、PH 帖子页；GitHub 没有这一层，为 null */
    val discussionUrl: String? = null,
    val score: Int = 0,
    val commentCount: Int = 0,
    /** HN 提交者 / PH maker。仅 Feed 入口有值——Picks 接口与本地收藏结构都不带这个字段 */
    val author: String? = null,
    val description: String? = null,
    val summary: String? = null,
    /** GitHub 主语言，仅 Trending 入口有值 */
    val language: String? = null,
    /** GitHub 总 star，仅 Trending 入口有值 */
    val stars: Int = 0,
) : Route {
    /** 兼任路由 key（见 [Route]），故自带页面身份 */
    override val screen = Screen.DIGEST

    val isGithub: Boolean get() = source == "github"
    val isProductHunt: Boolean get() = source == "producthunt"

    /** HN 自建帖（Ask HN / 纯讨论）：原文即讨论页，出路只显示一个按钮 */
    val isSelfPost: Boolean
        get() = discussionUrl != null &&
            (url.isBlank() || url == discussionUrl || url.contains("news.ycombinator.com"))

    /** GitHub 的 externalId 是 owner/repo，供「查看 README」进 RepoDetail；收藏的 `url:` 合成键不是仓库，不给按钮 */
    val githubOwnerRepo: Pair<String, String>?
        get() {
            if (!isGithub || externalId.startsWith("url:")) return null
            val parts = externalId.split("/", limit = 2)
            return if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) parts[0] to parts[1] else null
        }
}

fun hnDiscussionUrl(externalId: String): String =
    "https://news.ycombinator.com/item?id=$externalId"

/** 从 GitHub 仓库 url 反解 owner/repo（收藏、Picks 都只带 url）；query / fragment 不属于路径 */
private fun githubExternalId(url: String): String? {
    val path = url
        .substringBefore('#')
        .substringBefore('?')
        .removePrefix("https://github.com/")
        .removePrefix("http://github.com/")
        .trimEnd('/')
    val parts = path.split("/")
    return if (parts.size >= 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) "${parts[0]}/${parts[1]}" else null
}

fun FeedItem.toDigestPage(): DigestPage = DigestPage(
    source = source,
    externalId = externalId,
    title = title,
    url = url,
    discussionUrl = when (source) {
        "hackernews" -> extra?.hnUrl?.takeIf { it.isNotBlank() } ?: hnDiscussionUrl(externalId)
        "producthunt" -> extra?.phUrl?.takeIf { it.isNotBlank() }
        else -> null
    },
    score = score,
    commentCount = commentCount,
    author = author,
    description = description,
    summary = summary,
)

fun TrendingRepo.toDigestPage(summary: String?): DigestPage = DigestPage(
    source = "github",
    externalId = "$author/$repoName",
    title = "$author/$repoName",
    url = url,
    score = currentPeriodStars,
    description = description.takeIf { it.isNotBlank() },
    summary = summary,
    language = language,
    stars = stars,
)

fun PickItem.toDigestPage(): DigestPage {
    // Picks 的 GitHub 条目 title 只有仓库名，解读页统一按 owner/repo 展示（与 Trending / 收藏一致）
    val githubId = if (source == "github") githubExternalId(url) else null
    return DigestPage(
        source = source,
        externalId = githubId ?: externalId,
        title = githubId ?: title,
        url = url,
        discussionUrl = when (source) {
            "hackernews" -> hnDiscussionUrl(externalId)
            "producthunt" -> phUrl?.takeIf { it.isNotBlank() }
            else -> null
        },
        score = score,
        description = description,
        summary = summary,
    )
}

/**
 * 存量收藏的 externalId 可能是空串或 `url:<url>` 合成键（见 [FavoriteItem.resolvedExternalId]），
 * 且合成键会经云同步持久化——换设备拉取后本地 externalId 就是非空的合成值。
 * 故 HN 讨论区链接只认**纯数字** id（HN story id 恒为数字），否则回退收藏时记录的打开地址；
 * 解读接口查不到合成键会进「暂无解读」态，页面仍有可用出路。
 */
fun FavoriteItem.toDigestPage(): DigestPage = DigestPage(
    source = source,
    // GitHub 与 Picks 同一反解（剥 query / fragment），失败再回退云同步键
    externalId = if (source == "github") githubExternalId(url) ?: resolvedExternalId else resolvedExternalId,
    title = title,
    url = url,
    discussionUrl = when (source) {
        "hackernews" -> resolvedExternalId.takeIf { id -> id.isNotEmpty() && id.all(Char::isDigit) }
            ?.let { hnDiscussionUrl(it) } ?: targetUrl
        "producthunt" -> openUrl?.takeIf { it.isNotBlank() }
        else -> null
    },
    description = description,
    summary = summary,
)
