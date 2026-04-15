package whl.trending.ai.data.model

import kotlinx.serialization.Serializable

@Serializable
data class FavoriteItem(
    val url: String,
    val title: String,
    val source: String,
    val description: String? = null,
    val summary: String? = null,
    val savedAt: Long = 0L
)
