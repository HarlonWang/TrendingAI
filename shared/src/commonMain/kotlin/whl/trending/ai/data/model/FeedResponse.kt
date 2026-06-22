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
     * 其余来源仍使用 url。
     */
    val openUrl: String
        get() = if (source == "hackernews") {
            extra?.hnUrl?.takeIf { it.isNotBlank() } ?: url
        } else {
            url
        }
}

@Serializable
data class FeedExtra(
    @SerialName("hn_url")
    val hnUrl: String? = null
)
