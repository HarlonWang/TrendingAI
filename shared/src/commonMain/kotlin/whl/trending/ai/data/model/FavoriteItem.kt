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
     * 与后端 contents 表对齐的内容标识（github=owner/repo、hn=story id、ph=node id），
     * 云同步以 (source, externalId) 为唯一键。存量本地收藏 / 无法解析时为空串，
     * 首次同步由 [resolvedExternalId] best-effort 回填（github 从 url 反解，其余用 url 派生）。
     */
    val externalId: String = ""
) {
    /** 打开收藏时应使用的地址 */
    val targetUrl: String get() = openUrl?.takeIf { it.isNotBlank() } ?: url

    /**
     * 云同步用的 external_id：优先用已有值；缺失时 best-effort 回填——
     * github 从 url 反解 owner/repo，其余源用 url 派生合成键（`url:<url>`，仅保证用户内唯一，不与 contents join）。
     */
    val resolvedExternalId: String
        get() = externalId.ifBlank {
            if (source == "github") {
                val path = url
                    .removePrefix("https://github.com/")
                    .removePrefix("http://github.com/")
                    .trimEnd('/')
                val parts = path.split("/")
                if (parts.size >= 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                    "${parts[0]}/${parts[1]}"
                } else {
                    "url:$url"
                }
            } else {
                "url:$url"
            }
        }
}

