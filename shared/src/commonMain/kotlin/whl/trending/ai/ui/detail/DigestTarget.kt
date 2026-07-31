package whl.trending.ai.ui.detail

import whl.trending.ai.data.model.FavoriteItem
import whl.trending.ai.data.model.FeedItem
import whl.trending.ai.data.model.PickItem
import whl.trending.ai.data.model.SYNTHETIC_EXTERNAL_ID_PREFIX

/**
 * 进入 [DigestScreen] 所需的最小载荷（与导航路由解耦，便于各入口共用与单测）。
 *
 * [url] 与 [openUrl] 必须分开带：前者是条目的原始链接，也是收藏的主键
 * （[FavoriteItem.url] 恒为原始 url，全 app 按它判重与去重）；后者是「查看原文」
 * 实际打开的地址（HN 为讨论页、PH 为原帖）。对 HN/PH 两者必然不等，
 * 混用会让同一条目在列表与解读页各存一条收藏、星标状态互不可见。
 */
data class DigestTarget(
    val source: String,
    val externalId: String,
    val title: String,
    val url: String,
    val openUrl: String,
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
 * 缺失时返回 null，由调用方回落外开——不能拿 url 派生的合成键去请求，
 * 那在服务端 contents 里查不到，只会白跑一趟拿 404。
 *
 * 合成键要单独挡：[FavoriteItem.resolvedExternalId] 会把无 externalId 的存量收藏
 * 回填成 `url:<url>`（见 FavoriteRepository 的 add/withResolvedId），
 * 只判空挡不住它——那种收藏拿到的 externalId 非空但对服务端无意义。
 */
fun digestTargetOf(
    source: String,
    externalId: String?,
    title: String,
    url: String,
    openUrl: String = url,
    summary: String? = null,
): DigestTarget? {
    if (source !in DIGEST_SOURCES) return null
    if (externalId.isNullOrBlank() || externalId.startsWith(SYNTHETIC_EXTERNAL_ID_PREFIX)) return null
    if (url.isBlank()) return null
    return DigestTarget(source, externalId, title, url, openUrl.ifBlank { url }, summary)
}

fun FeedItem.digestTarget(): DigestTarget? =
    digestTargetOf(source, externalId, title, url, openUrl, summary)

fun PickItem.digestTarget(): DigestTarget? =
    digestTargetOf(source, externalId, title, url, openUrl, summary)

fun FavoriteItem.digestTarget(): DigestTarget? =
    digestTargetOf(source, externalId, title, url, targetUrl, summary)
