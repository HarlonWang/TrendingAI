package whl.trending.ai.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import whl.trending.ai.auth.globalAuthManager
import whl.trending.ai.data.local.SettingsManager
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.data.model.FavoriteItem
import whl.trending.ai.data.model.PendingFavoriteOp
import whl.trending.ai.data.remote.TrendingApi

/**
 * 收藏云同步引擎（设计稿 2026-07-24-favorites-cloud-sync，B 档：本地缓存 + 幂等 CRUD + 打开时全量拉取覆盖）。
 *
 * - 本地缓存（[SettingsManager] 的 favorites）始终是 UI 即时真源，离线可用；网络在后台跑、失败不打断。
 * - 收藏/取消：先落本地即时生效，登录且已完成首次合并后入队一条 op 并后台 flush；失败留队列下次重试。
 * - [sync]（登录/启动触发）：首次登录把全部本地收藏 batch 上云合并，之后 flush 增量 op；随后全量 GET 覆盖本地。
 * - 删除跨设备传播靠「打开时全量 GET 覆盖」完成，无墓碑。
 *
 * UI 只需把原先直调 SettingsManager 的收藏读写改为调本类；收藏列表流仍读 SettingsManager.favorites 不变。
 */
class FavoriteRepository(
    private val settings: SettingsManager,
    private val api: TrendingApi,
    /**
     * 带鉴权执行一次请求（见 [whl.trending.ai.auth.AuthManager.authorized]）：
     * 拿 token 交给 block，**401 时刷新后重试一次**；无会话则不执行 block。
     *
     * 从「取 token 再自己发请求」换成这个形态，是因为收藏同步常发生在 App 启动或
     * 回前台的瞬间——access token 恰好过期时，旧写法会静默失败、把用户的收藏留在
     * 待推队列里，下次再撞。
     */
    private val authorized: suspend (suspend (String) -> Unit) -> Unit,
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
     * 登录 / 启动时的同步。传入 access token（null=匿名，直接返回，纯本地）。
     * 内部吞掉网络异常：失败保留本地态，下次再试。
     */
    suspend fun sync(accessToken: String?) {
        val token = accessToken ?: return
        mutex.withLock {
            runCatching {
                if (!settings.favoritesMerged()) {
                    // 首次登录合并：全部本地收藏（含匿名期 + 存量，externalId 回填）batch 上云
                    val local = settings.currentFavorites().map { withResolvedId(it) }
                    if (local.isNotEmpty() && !api.batchPutFavorites(token, local)) {
                        return@runCatching // batch 失败：不置 merged、不覆盖本地，下次重试
                    }
                    settings.setPendingFavoriteOps(emptyList()) // batch 已全覆盖，清历史 op
                    settings.setFavoritesMerged(true)
                } else {
                    flushPending(token)
                }
                // 全量拉取覆盖本地；叠加在途/未 flush 成功的 pending，避免刚发生的本地改动被旧快照冲掉
                val server = api.fetchFavorites(token)
                val merged = applyPending(server, settings.getPendingFavoriteOps())
                settings.replaceFavorites(merged)
            }
        }
    }

    /**
     * 触发一次同步，运行在仓库自有 [scope] 上（**不绑定任何 Compose composition**）。
     * 必须用它、而非在 composable 的 LaunchedEffect 里直接 await [sync]——否则用户登录后
     * 一旦离开当前屏（如登录发生在账户页、随即进"我的收藏"），composition 退出会取消协程
     * （LeftCompositionCancellationException），[sync] 半途中断、[replaceFavorites] 不执行，
     * 云端收藏拉不下来。token 由自身 [tokenProvider] 取，调用方无需持有。
     */
    fun requestSync() {
        scope.launch {
            authorized { sync(it) }
        }
    }

    /**
     * 登出清理：清空本地收藏 + 同步状态，避免账号间串味。由登出流程调用。
     *
     * 取舍：会一并清掉未 flush 成功的 pending op。极端场景「离线新增一条收藏（flush 失败留队列）
     * → 立即登出」下，该条既未上云也被清除 → 丢失。不在此做 best-effort flush，因为：
     * (1) 该场景前提是离线，flush 同样发不出去；(2) 登出流程开头已吊销凭证，此刻已无有效 token。
     * 唯一能不丢的做法是「登出不清、留到下次登录再传」，但那会重开要防的账号串味。
     * 详见设计稿 §6.2。
     */
    fun onSignOut() {
        settings.clearFavoritesOnSignOut()
    }

    // ---- 内部 ----

    /**
     * 仅在已完成首次合并后才入队增量 op；否则本地改动由首次 batch 合并整体覆盖。
     * merged 只有在带真实 token 的 [sync] 成功后才置 true，故匿名 / iOS(Noop 无 token) 恒为 false，
     * 天然不入队、队列不膨胀，无需再看平台是否支持登录。
     */
    private fun canSyncOps(): Boolean = settings.favoritesMerged()

    /**
     * 入队一条 op 并尝试 flush，全程持 [mutex]——与 [sync] 串行化。
     * 关键：pending 键的读-改-写只允许在 mutex 内发生（enqueue/flush/sync 都在锁内），
     * 否则主线程 enqueue 与后台 flush 并行 RMW 同一 prefs 键会 lost-update、静默丢一条收藏。
     */
    private suspend fun pushOp(op: PendingFavoriteOp) {
        mutex.withLock {
            enqueueLocked(op)
            // 匿名/无会话：留队列，下次 sync 再推
            runCatching { authorized { flushPending(it) } }
        }
    }

    /** 必须在 [mutex] 内调用。同一 (op,url) 去重合并：保留最新一条，避免同键 op 堆积。 */
    private fun enqueueLocked(op: PendingFavoriteOp) {
        val kept = settings.getPendingFavoriteOps().filterNot { it.op == op.op && it.url == op.url }
        settings.setPendingFavoriteOps(kept + op)
    }

    /** 逐条推送队列；成功的移出队列，遇失败保留剩余（含当前）待下次重试。必须在 [mutex] 内调用。 */
    private suspend fun flushPending(token: String) {
        val ops = settings.getPendingFavoriteOps()
        if (ops.isEmpty()) return
        val done = mutableListOf<PendingFavoriteOp>()
        for (op in ops) {
            val ok = when (op.op) {
                "add" -> op.item?.let { api.putFavorite(token, withResolvedId(it)) } ?: true
                "delete" -> api.deleteFavorite(token, op.source, op.externalId)
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

/** 全局单例：注入全局 SettingsManager / TrendingApi / 登录态 token。 */
val globalFavoriteRepository by lazy {
    FavoriteRepository(
        settings = globalSettingsManager,
        api = TrendingApi(),
        authorized = { block -> globalAuthManager.authorized(block) },
    )
}
