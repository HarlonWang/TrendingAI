package whl.trending.ai.data.model

import kotlinx.serialization.Serializable

@Serializable
data class FavoriteItem(
    /** 收藏主键，同时用于本地判重与去重；恒为条目原始 url，不随打开地址变化 */
    val url: String,
    val title: String,
    val source: String,
    val description: String? = null,
    val summary: String? = null,
    val savedAt: Long = 0L,
    /**
     * 点击收藏时实际打开的地址（如 PH 条目的原帖链接）。
     * 与 [url] 一致或未记录时为 null；旧版本存的收藏均为 null，回退用 [url] 打开。
     */
    val openUrl: String? = null,
    /**
     * 与后端 contents 表对齐的内容标识（github=owner/repo、hn=story id、ph=node id）。
     * 各页面收藏时都自带（来自 API），仅 0.22.0 之前的存量本地收藏为空串——
     * 上云前由 `FavoriteRepository.withExternalId` 用 url 顶上，不做反解。
     */
    val externalId: String = ""
) {
    /** 打开收藏时应使用的地址 */
    val targetUrl: String get() = openUrl?.takeIf { it.isNotBlank() } ?: url
}

