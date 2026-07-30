package whl.trending.ai.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FeedResponse(
    val success: Boolean = false,
    val count: Int = 0,
    val data: List<FeedItem> = emptyList()
)

@Serializable
data class FeedItem(
    val source: String = "",
    val externalId: String = "",
    val title: String = "",
    val url: String = "",
    val description: String? = null,
    val author: String? = null,
    val score: Int = 0,
    val commentCount: Int = 0,
    val tags: List<String> = emptyList(),
    val extra: FeedExtra? = null,
    val firstSeenAt: String? = null,
    val summary: String? = null
) {
    /**
     * 点击条目时应打开的链接。
     * Hacker News 条目优先打开讨论页（extra.hn_url），避免外链文章站点不可达；
     * Product Hunt 条目优先打开 PH 原帖（extra.ph_url），保留评论区并带上官方归因；
     * 其余来源仍使用 url。
     */
    val openUrl: String
        get() = when (source) {
            "hackernews" -> extra?.hnUrl?.takeIf { it.isNotBlank() } ?: url
            "producthunt" -> extra?.phUrl?.takeIf { it.isNotBlank() } ?: url
            else -> url
        }

    /**
     * Product Hunt 产品缩略图，按显示尺寸向 imgix 请求裁剪版本。
     * 原图动辄 100KB+ 且尺寸不一，带上 w/h/fit 后单张降到几 KB；
     * auto=format 会把 svg/gif 一并栅格化成 png/webp，因此不需要额外的解码器依赖。
     */
    fun thumbnailUrl(px: Int): String? {
        if (source != "producthunt") return null
        val raw = extra?.thumbnailUrl?.takeIf { it.isNotBlank() } ?: return null
        val separator = if ('?' in raw) "&" else "?"
        return "$raw${separator}auto=format&w=$px&h=$px&fit=crop"
    }

    /**
     * Product Hunt 产品主视觉，取图库首图。
     * fit=max 只按宽度约束、不裁剪，原始比例交给调用方按图片实际尺寸排布。
     * 原图大到 4MB，必须带尺寸参数；这里显式要 webp 而不是 auto=format——
     * 图库多为界面截图，auto 会选无损 png，同尺寸下比 webp 大好几倍。
     * 2026-07-30 之前入库的条目没有 gallery，返回 null 由调用方降级。
     */
    fun heroImageUrl(widthPx: Int): String? {
        if (source != "producthunt") return null
        val raw = extra?.gallery?.firstOrNull()?.takeIf { it.isNotBlank() } ?: return null
        val separator = if ('?' in raw) "&" else "?"
        return "$raw${separator}fm=webp&q=70&w=$widthPx&fit=max"
    }
}

@Serializable
data class FeedExtra(
    @SerialName("hn_url")
    val hnUrl: String? = null,
    @SerialName("ph_url")
    val phUrl: String? = null,
    @SerialName("thumbnail_url")
    val thumbnailUrl: String? = null,
    val gallery: List<String> = emptyList()
)
