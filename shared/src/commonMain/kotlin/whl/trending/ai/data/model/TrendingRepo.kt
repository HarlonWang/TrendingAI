package whl.trending.ai.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TrendingRepo(
    val rank: Int = 0,
    val author: String = "",
    val repoName: String = "",
    val url: String = "",
    val description: String = "",
    val language: String? = null,
    val languageColor: String? = null,
    val stars: Int = 0,
    val forks: Int = 0,
    val currentPeriodStars: Int = 0,
    // 新上榜标记：首登 daily 全语言榜 ≤ 24 小时的项目（由后端 isNew 计算，仅 daily+all 视图为 true）
    val isNew: Boolean = false,
    val builtBy: List<TrendingContributor> = emptyList(),
    val aiSummaries: List<TrendingAiSummary> = emptyList()
)

/**
 * Trending API 顶层响应结构。
 */
@Serializable
data class TrendingResponse(
    val success: Boolean = false,
    val count: Int = 0,
    val metadata: TrendingMetadata = TrendingMetadata(),
    val data: List<TrendingRepo> = emptyList()
)

/**
 * API 返回的元数据信息。
 */
@Serializable
data class TrendingMetadata(
    val since: String = "",
    val lang: String = "",
    @SerialName("summary_lang")
    val summaryLang: String = "",
    val providers: List<String> = emptyList(),
    val date: String = "",
    val batch: String = "",
    @SerialName("captured_at")
    val capturedAt: String = ""
)

/**
 * 仓库贡献者基础信息。
 */
@Serializable
data class TrendingContributor(
    val username: String = "",
    val avatar: String = ""
)

/**
 * AI 摘要内容及来源。
 */
@Serializable
data class TrendingAiSummary(
    val provider: String = "",
    val content: String = ""
)
