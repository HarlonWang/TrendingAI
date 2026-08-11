package whl.trending.ai.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PicksResponse(
    val success: Boolean = false,
    val metadata: PicksMetadata = PicksMetadata(),
    val debut: List<PickItem> = emptyList(),
    val speedRead: List<PickItem> = emptyList(),
    val deepDive: List<PickItem> = emptyList(),
    val controversy: List<PickItem> = emptyList()
)

@Serializable
data class PicksMetadata(
    val date: String = "",
    /** 本批 picks 的生成时刻（UTC）。0.24.0 之前的后端不返回，缺省空串表示无从得知。 */
    @SerialName("generated_at")
    val generatedAt: String = ""
)

@Serializable
data class PickItem(
    val rank: Int = 0,
    val source: String = "",
    /** 与后端 contents 对齐的内容标识；供收藏云同步作唯一键 */
    val externalId: String = "",
    val title: String = "",
    val url: String = "",
    val phUrl: String? = null,
    val description: String? = null,
    val score: Int = 0,
    val aiScore: Double = 0.0,
    val sourceLabel: String = "",
    val alsoOn: List<String> = emptyList(),
    val summary: String? = null,
    val analysis: PickAnalysis? = null
) {
    /**
     * 点击/分享条目时应使用的链接。
     * Product Hunt 条目优先打开 PH 原帖（phUrl），保留评论区并带上官方归因；其余来源仍使用 url。
     */
    val openUrl: String
        get() = if (source == "producthunt") {
            phUrl?.takeIf { it.isNotBlank() } ?: url
        } else {
            url
        }
}

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
