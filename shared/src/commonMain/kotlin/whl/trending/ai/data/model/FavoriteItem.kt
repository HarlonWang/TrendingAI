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
    /** 点击收藏时实际打开的地址；与 [url] 一致或未记录（旧版本收藏）时为 null，回退用 [url]。 */
    val openUrl: String? = null,
    /**
     * 与后端 contents 表对齐的内容标识，云同步以 (source, externalId) 为唯一键。
     * 存量收藏 / 无法解析时为空串，由 [resolvedExternalId] best-effort 回填。
     */
    val externalId: String = ""
) {
    val targetUrl: String get() = openUrl?.takeIf { it.isNotBlank() } ?: url

    /**
     * 云同步用的 external_id：缺失时 github 从 url 反解 owner/repo，
     * 其余源用 `url:<url>` 合成键（仅保证用户内唯一，不与 contents join）。
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

