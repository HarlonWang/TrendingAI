package whl.trending.ai.data.model

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
    val firstSeenAt: String? = null,
    val summary: String? = null
)
