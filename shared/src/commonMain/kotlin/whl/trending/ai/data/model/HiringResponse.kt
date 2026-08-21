package whl.trending.ai.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GET /api/hiring 响应 —— HN「Ask HN: Who is hiring?」月度专题。
 *
 * **一次返回整月全量、不分页**，是「不做个性化」的直接红利：一份数据全球通用、
 * 可做成单个 CDN 对象，筛选与计数全在客户端本地算，用户点筛选零往返。
 * 实测 233 条约 187KB（gzip 50KB）。服务端注释里标了破坏性变更点：
 * 超过 500KB 或 1000 条时才改分页，届时本模型要跟着改。
 *
 * **产品硬边界**：这里只有原文写明的**约束事实**，没有也不允许有「可投性」字段。
 * 能不能投由读者结合自身情况判断——我们既测不准他在哪（代理、海外用户），
 * 也不该把「有绿卡 / 愿 relocate / 有海外实体」这种多变量个人决策压成系统推断。
 * 不要新增 canApply / isEligible 之类的派生字段，也不要按设备地区做默认过滤。
 */
@Serializable
data class HiringResponse(
    val success: Boolean = false,
    val month: String = "",
    @SerialName("story_id")
    val storyId: String = "",
    @SerialName("posted_at")
    val postedAt: String = "",
    @SerialName("last_synced_at")
    val lastSyncedAt: String? = null,
    /** 可用月份列表，倒序。归档切换用，与列表同一次请求返回 */
    val months: List<String> = emptyList(),
    /** 服务端预算好的分档计数，供筛选器直出。客户端筛选后的联动计数自己本地算 */
    val facets: HiringFacets = HiringFacets(),
    val posts: List<HiringPost> = emptyList(),
    val code: String? = null,
)

@Serializable
data class HiringFacets(
    @SerialName("region_scope")
    val regionScope: Map<String, Int> = emptyMap(),
    @SerialName("remote_kind")
    val remoteKind: Map<String, Int> = emptyMap(),
    val employment: Map<String, Int> = emptyMap(),
    @SerialName("allowed_regions")
    val allowedRegions: Map<String, Int> = emptyMap(),
)

@Serializable
data class HiringPost(
    @SerialName("external_id")
    val externalId: String,
    @SerialName("posted_at")
    val postedAt: String,
    val company: String? = null,
    val roles: List<String> = emptyList(),
    @SerialName("tech_stack")
    val techStack: List<String> = emptyList(),
    val employment: String? = null,
    /** 原文原样，不换算币种、不归一化；原文没写则 null */
    @SerialName("salary_raw")
    val salaryRaw: String? = null,
    @SerialName("apply_url")
    val applyUrl: String? = null,

    // —— 地域与准入约束（原文写明的事实，不是判断）——
    /** remote / onsite / hybrid / unspecified */
    @SerialName("remote_kind")
    val remoteKind: String = "unspecified",
    /**
     * worldwide / restricted / unspecified。
     * **unspecified 不等于 worldwide**：前者是原文没提（信息缺失），后者是原文明确写了不限。
     * 呈现上必须分开，合并等于把缺失伪造成事实。
     */
    @SerialName("region_scope")
    val regionScope: String = "unspecified",
    @SerialName("allowed_regions")
    val allowedRegions: List<String> = emptyList(),
    /** 原文原样，如 "CET ±3"、"Hiring GMT-8 to GMT+2" */
    @SerialName("timezone_req")
    val timezoneReq: String? = null,
    @SerialName("language_req")
    val languageReq: String? = null,
    @SerialName("onsite_cities")
    val onsiteCities: List<String> = emptyList(),
    /** 签证/工作授权表述，原文原样，正反两向都有：既有 "not sponsoring visas"，也有 "Visa | Relocation" */
    @SerialName("work_authorization")
    val workAuthorization: String? = null,

    /** 按请求的 lang 取 zh 或 en 列；事实字段与语言无关 */
    val title: String? = null,
    val summary: String? = null,
) {
    /** HN 评论永久链接。comment id 即 externalId，不需要服务端额外存 URL */
    val hnUrl: String get() = "https://news.ycombinator.com/item?id=$externalId"
}
