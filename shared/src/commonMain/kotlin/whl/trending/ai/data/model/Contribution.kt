package whl.trending.ai.data.model

import kotlinx.serialization.Serializable

/**
 * GitHub 贡献日历（绿色热力图）的干净领域模型。
 * 数据仅来自 GitHub GraphQL API（REST 不暴露 contribution calendar），
 * UI 只依赖本模型，与 GraphQL 响应的深层嵌套结构解耦。
 * @Serializable 供 Profile 上次数据缓存（ProfileCache）序列化落盘。
 */
@Serializable
data class ContributionCalendar(
    /** 最近一年的总贡献数 */
    val total: Int,
    /** 按周分组，每周含 7 天（首尾周可能不足 7 天） */
    val weeks: List<ContributionWeek>,
)

@Serializable
data class ContributionWeek(
    val days: List<ContributionDay>,
)

@Serializable
data class ContributionDay(
    /** ISO 日期，如 "2026-06-23" */
    val date: String,
    /** 0=周日 … 6=周六，对齐 GitHub */
    val weekday: Int,
    val count: Int,
    val level: ContributionLevel,
)

/** GitHub 直接返回的五档强度，省得自算分位；UI 据此映射颜色深浅。 */
enum class ContributionLevel {
    NONE,
    FIRST,
    SECOND,
    THIRD,
    FOURTH;

    companion object {
        /** 映射 GraphQL 的 contributionLevel 枚举字符串；未知值兜底为 NONE。 */
        fun fromRaw(raw: String): ContributionLevel = when (raw) {
            "FIRST_QUARTILE" -> FIRST
            "SECOND_QUARTILE" -> SECOND
            "THIRD_QUARTILE" -> THIRD
            "FOURTH_QUARTILE" -> FOURTH
            else -> NONE
        }
    }
}
