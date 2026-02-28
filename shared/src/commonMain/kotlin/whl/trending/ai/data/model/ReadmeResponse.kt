package whl.trending.ai.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ReadmeResponse(
    val success: Boolean = false,
    val owner: String = "",
    val repo: String = "",
    val branch: String = "",
    val filename: String = "",
    val content: String = "",
    val error: String? = null
)
