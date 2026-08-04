package whl.trending.ai.data.model

import kotlinx.serialization.Serializable

/**
 * GET /api/favorites 响应：服务端返回的收藏全量列表（字段名与 [FavoriteItem] 对齐，可直接反序列化）。
 * 同一形状也用作 POST /api/favorites/batch 的请求体。
 */
@Serializable
data class FavoritesResponse(
    val favorites: List<FavoriteItem> = emptyList()
)
