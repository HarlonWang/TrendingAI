package whl.trending.ai.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PicksResponse(
    val success: Boolean = false,
    val metadata: PicksMetadata = PicksMetadata(),
    val speedRead: List<PickItem> = emptyList(),
    val deepDive: List<PickItem> = emptyList(),
    val controversy: List<PickItem> = emptyList()
)

@Serializable
data class PicksMetadata(
    val date: String = ""
)

@Serializable
data class PickItem(
    val rank: Int = 0,
    val source: String = "",
    val title: String = "",
    val url: String = "",
    val description: String? = null,
    val score: Int = 0,
    val aiScore: Double = 0.0,
    val sourceLabel: String = "",
    val alsoOn: List<String> = emptyList(),
    val summary: String? = null,
    val analysis: PickAnalysis? = null
)

@Serializable
data class PickAnalysis(
    val core: String = "",
    @SerialName("why_important")
    val whyImportant: String = "",
    @SerialName("community_voice")
    val communityVoice: CommunityVoice = CommunityVoice(),
    val action: String? = null,
    val alternatives: String? = null,
    val terms: List<String>? = null
)

@Serializable
data class CommunityVoice(
    val positive: String = "",
    val negative: String = ""
)
