package whl.trending.ai.data.model

import kotlinx.serialization.Serializable

/** GET /api/favorites 响应：服务端返回的收藏全量列表（字段名与 [FavoriteItem] 对齐，可直接反序列化）。 */
@Serializable
data class FavoritesResponse(
    val favorites: List<FavoriteItem> = emptyList()
)

/**
 * 收藏本地待同步操作（pending op），登录且已完成首次合并后产生：
 * 每次收藏/取消先落本地即时生效，再入队一条 op，后台 flush 到服务端；flush 失败留队列下次重试。
 * delete 需 (source, externalId) 打服务端；[url] 供本地缓存合并（applyPending）按 url 定位。
 */
@Serializable
data class PendingFavoriteOp(
    /** "add" | "delete" */
    val op: String,
    val url: String,
    val source: String,
    val externalId: String,
    /** add 时携带完整快照，供 flush 上云与在途合并回填 */
    val item: FavoriteItem? = null
)
