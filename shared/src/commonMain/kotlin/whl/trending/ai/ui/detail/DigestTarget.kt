package whl.trending.ai.ui.detail

import whl.trending.ai.data.model.FavoriteItem
import whl.trending.ai.data.model.FeedItem
import whl.trending.ai.data.model.PickItem

/** 进入 [DigestScreen] 所需的最小载荷（与导航路由解耦，便于各入口共用与单测） */
data class DigestTarget(
    val source: String,
    val externalId: String,
    val title: String,
    val url: String,
    val summary: String? = null,
)

/**
 * 支持 AI 解读的来源。
 *
 * GitHub 不在其中——它有 README 详情页（[ReadmeScreen]）可读，解读作为页内入口存在，
 * 不需要再抢占条目点击。其余未知来源一律回落到原有的「外开链接」。
 */
private val DIGEST_SOURCES = setOf("hackernews", "producthunt")

/**
 * 条目能否进解读页。externalId 是服务端 detail-summary 的必需入参，
 * 缺失时（存量本地收藏没有该字段）返回 null，由调用方回落外开——不能拿 url 派生的
 * 合成键去请求，那在服务端 contents 里查不到，只会白跑一趟拿 404。
 */
fun digestTargetOf(
    source: String,
    externalId: String?,
    title: String,
    url: String,
    summary: String? = null,
): DigestTarget? {
    if (source !in DIGEST_SOURCES) return null
    if (externalId.isNullOrBlank()) return null
    if (url.isBlank()) return null
    return DigestTarget(source, externalId, title, url, summary)
}

fun FeedItem.digestTarget(): DigestTarget? =
    digestTargetOf(source, externalId, title, openUrl, summary)

fun PickItem.digestTarget(): DigestTarget? =
    digestTargetOf(source, externalId, title, openUrl, summary)

fun FavoriteItem.digestTarget(): DigestTarget? =
    digestTargetOf(source, externalId, title, targetUrl, summary)
