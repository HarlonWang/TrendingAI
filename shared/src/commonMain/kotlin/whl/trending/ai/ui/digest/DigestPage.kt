package whl.trending.ai.ui.digest

import whl.trending.ai.data.model.FavoriteItem
import whl.trending.ai.data.model.FeedItem
import whl.trending.ai.data.model.PickItem

/**
 * HN 解读页路由参数（Nav3 backStack key）。
 *
 * 列表已有的数据全部带进来：头部（标题/热度）即时渲染不等网络；
 * 收藏落库沿用与列表完全相同的字段（url 主键 + source/externalId 云同步键），
 * 保证解读页收藏与列表收藏是同一条记录。
 */
data class DigestPage(
    val source: String,
    val externalId: String,
    val title: String,
    /** 原文链接（自建帖时与 [hnUrl] 相同） */
    val url: String,
    /** HN 讨论区链接 */
    val hnUrl: String,
    val score: Int = 0,
    val commentCount: Int = 0,
    /** HN 提交者。仅 Feed 入口有值——Picks 接口与本地收藏结构都不带这个字段 */
    val author: String? = null,
    val description: String? = null,
    val summary: String? = null,
) {
    /** 自建帖（Ask HN / 纯讨论）：原文即讨论页，出路只显示一个按钮 */
    val isSelfPost: Boolean
        get() = url.isBlank() || url == hnUrl || url.contains("news.ycombinator.com")
}

fun hnDiscussionUrl(externalId: String): String =
    "https://news.ycombinator.com/item?id=$externalId"

fun FeedItem.toDigestPage(): DigestPage = DigestPage(
    source = source,
    externalId = externalId,
    title = title,
    url = url,
    hnUrl = extra?.hnUrl?.takeIf { it.isNotBlank() } ?: hnDiscussionUrl(externalId),
    score = score,
    commentCount = commentCount,
    author = author,
    description = description,
    summary = summary,
)

fun PickItem.toDigestPage(): DigestPage = DigestPage(
    source = source,
    externalId = externalId,
    title = title,
    url = url,
    hnUrl = hnDiscussionUrl(externalId),
    score = score,
    description = description,
    summary = summary,
)

/**
 * 存量收藏的 externalId 可能是空串或 `url:<url>` 合成键（见 [FavoriteItem.resolvedExternalId]），
 * 且合成键会经云同步持久化——换设备拉取后本地 externalId 就是非空的合成值。
 * 故讨论区链接只认**纯数字** id（HN story id 恒为数字），否则回退收藏时记录的打开地址；
 * 解读接口查不到合成键会进「暂无解读」态，页面仍有可用出路。
 */
fun FavoriteItem.toDigestPage(): DigestPage = DigestPage(
    source = source,
    externalId = resolvedExternalId,
    title = title,
    url = url,
    hnUrl = resolvedExternalId.takeIf { id -> id.isNotEmpty() && id.all(Char::isDigit) }
        ?.let { hnDiscussionUrl(it) } ?: targetUrl,
    description = description,
    summary = summary,
)
