package whl.trending.ai.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import whl.trending.ai.auth.AuthState
import whl.trending.ai.auth.globalAuthManager
import whl.trending.ai.data.local.SettingsManager
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.data.model.FavoriteItem
import whl.trending.ai.data.model.PendingFavoriteOp
import whl.trending.ai.data.remote.TrendingApi

/**
 * 收藏云同步引擎。本地缓存是 UI 即时真源，网络后台跑、失败留队列重试；
 * [sync] 首次登录 batch 上云合并、之后 flush 增量 op，随后全量 GET 覆盖本地——
 * 删除的跨设备传播就靠这次覆盖，无墓碑。收藏列表流仍读 SettingsManager.favorites。
 */
class FavoriteRepository(
    private val settings: SettingsManager,
    private val api: TrendingApi,
    /**
     * 是否已登录。鉴权本身由 ktor `Auth` 插件统一处理（见 `TrendingAuth.kt`），这里只用来
     * 在匿名期短路——避免明知没会话还发一轮必然 401 的请求。
     */
    private val loggedIn: () -> Boolean = { globalAuthManager.authState.value is AuthState.LoggedIn },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    // 序列化所有网络同步，避免 flush 与全量覆盖交错导致缓存被旧快照回冲
    private val mutex = Mutex()

    /** 收藏：本地即时写入 + 埋点（沿用 SettingsManager 手势路径），随后后台推送。 */
    fun add(item: FavoriteItem) {
        val resolved = if (item.externalId.isBlank()) item.copy(externalId = item.resolvedExternalId) else item
        settings.addFavorite(resolved)
        if (canSyncOps()) {
            scope.launch { pushOp(PendingFavoriteOp("add", resolved.url, resolved.source, resolved.externalId, resolved)) }
        }
    }

    /** 取消收藏：按 url 本地删除 + 埋点，随后后台推送删除。 */
    fun remove(url: String) {
        val existing = settings.currentFavorites().firstOrNull { it.url == url }
        settings.removeFavorite(url)
        if (existing != null && canSyncOps()) {
            scope.launch { pushOp(PendingFavoriteOp("delete", url, existing.source, existing.resolvedExternalId, null)) }
        }
    }

    /**
     * 登录 / 启动时的同步。匿名直接返回，纯本地。
     * 内部吞掉网络异常：失败保留本地态，下次再试。
     */
    suspend fun sync() {
        if (!loggedIn()) return
        mutex.withLock {
            runCatching {
                if (!settings.favoritesMerged()) {
                    // 首次登录合并：全部本地收藏（含匿名期 + 存量，externalId 回填）batch 上云
                    val local = settings.currentFavorites().map { withResolvedId(it) }
                    if (local.isNotEmpty() && !api.batchPutFavorites(local)) {
                        return@runCatching // batch 失败：不置 merged、不覆盖本地，下次重试
                    }
                    settings.setPendingFavoriteOps(emptyList()) // batch 已全覆盖，清历史 op
                    settings.setFavoritesMerged(true)
                } else {
                    flushPending()
                }
                // 全量拉取覆盖本地；叠加在途/未 flush 成功的 pending，避免刚发生的本地改动被旧快照冲掉
                val server = api.fetchFavorites()
                val merged = applyPending(server, settings.getPendingFavoriteOps())
                settings.replaceFavorites(merged)
            }
        }
    }

    /**
     * 触发一次同步，运行在仓库自有 [scope]（不绑定 composition）。必须用它、而非在
     * LaunchedEffect 里直接 await [sync]——composition 退出会取消协程，[sync] 半途中断、
     * 云端收藏拉不下来。
     */
    fun requestSync() {
        scope.launch {
            sync()
        }
    }

    /** 登出清理，避免账号间串味。pending op 一并清掉不 flush——凭证已吊销，也发不出去。 */
    fun onSignOut() {
        settings.clearFavoritesOnSignOut()
    }

    /**
     * 仅在已完成首次合并后才入队增量 op（此前本地改动由首次 batch 整体覆盖）；
     * merged 只在登录态的 [sync] 成功后置 true，匿名恒 false、天然不入队。
     */
    private fun canSyncOps(): Boolean = settings.favoritesMerged()

    /**
     * 入队一条 op 并尝试 flush，全程持 [mutex]。pending 键的读-改-写只允许在 mutex 内发生，
     * 否则并行 RMW 同一 prefs 键会 lost-update、静默丢收藏。
     */
    private suspend fun pushOp(op: PendingFavoriteOp) {
        mutex.withLock {
            enqueueLocked(op)
            // 匿名/无会话：留队列，下次 sync 再推
            if (loggedIn()) runCatching { flushPending() }
        }
    }

    /** 必须在 [mutex] 内调用。同一 (op,url) 去重合并：保留最新一条，避免同键 op 堆积。 */
    private fun enqueueLocked(op: PendingFavoriteOp) {
        val kept = settings.getPendingFavoriteOps().filterNot { it.op == op.op && it.url == op.url }
        settings.setPendingFavoriteOps(kept + op)
    }

    /** 逐条推送队列；成功的移出队列，遇失败保留剩余（含当前）待下次重试。必须在 [mutex] 内调用。 */
    private suspend fun flushPending() {
        val ops = settings.getPendingFavoriteOps()
        if (ops.isEmpty()) return
        val done = mutableListOf<PendingFavoriteOp>()
        for (op in ops) {
            val ok = when (op.op) {
                "add" -> op.item?.let { api.putFavorite(withResolvedId(it)) } ?: true
                "delete" -> api.deleteFavorite(op.source, op.externalId)
                else -> true // 未知 op 直接丢弃
            }
            if (ok) done += op else break
        }
        if (done.isNotEmpty()) {
            settings.setPendingFavoriteOps(settings.getPendingFavoriteOps().filterNot { it in done })
        }
    }

    private fun withResolvedId(item: FavoriteItem): FavoriteItem =
        if (item.externalId.isBlank()) item.copy(externalId = item.resolvedExternalId) else item

    /** 服务端全量叠加本地 pending：add 覆盖入表、delete 移除，按 url 定位，收藏时刻倒序。 */
    private fun applyPending(server: List<FavoriteItem>, pending: List<PendingFavoriteOp>): List<FavoriteItem> {
        val map = LinkedHashMap<String, FavoriteItem>()
        server.forEach { map[it.url] = it }
        pending.forEach { op ->
            when (op.op) {
                "add" -> op.item?.let { map[it.url] = it }
                "delete" -> map.remove(op.url)
            }
        }
        return map.values.sortedByDescending { it.savedAt }
    }
}

/** 全局单例：注入全局 SettingsManager / TrendingApi。 */
val globalFavoriteRepository by lazy {
    FavoriteRepository(
        settings = globalSettingsManager,
        api = TrendingApi(),
    )
}
