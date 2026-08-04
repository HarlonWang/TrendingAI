package whl.trending.ai.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import whl.trending.ai.auth.AuthManager
import whl.trending.ai.auth.AuthState
import whl.trending.ai.auth.globalAuthManager
import whl.trending.ai.core.platform.trackEvent
import whl.trending.ai.data.local.SettingsManager
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.data.model.FavoriteItem
import whl.trending.ai.data.remote.TrendingApi

/**
 * 收藏读写的唯一入口。一条数据永远只有一个真源，同步只有一个方向：
 *
 * - **未登录**：[SettingsManager.localFavorites] 是唯一真源，纯本地、离线可用（与云同步上线前一致）。
 * - **登录那一刻**：把 local 那份一次性 batch 上云，成功后清空——这是全链路唯一一次「本地 → 云端」。
 * - **已登录**：服务端是唯一真源，[SettingsManager.cachedFavorites] 只是它的只读镜像；
 *   收藏/取消先乐观改镜像再打接口，失败原样回滚并发 [errors]（离线不支持收藏，是有意的功能裁剪）。
 *
 * 因此这里没有 pending 队列、没有 dirty/merged 状态位、没有双向合并与冲突判定：
 * 「local 非空」本身就是「还欠一次导入」的信号，导入失败不清空、下次 [sync] 原样重来即可（batch 幂等）。
 */
class FavoriteRepository(
    private val settings: SettingsManager,
    private val api: TrendingApi,
    /**
     * 取当前 [AuthManager] 而非直接持有实例：配置变更会重建 Activity 并替换 [globalAuthManager]，
     * 持有旧实例会让登录态判断永久停在注入前的 Noop（登录后仍走本地分支）。
     */
    private val authProvider: () -> AuthManager = { globalAuthManager },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    /** 埋点注入点：host 单测里 Aptabase 未初始化，直调 trackEvent 会 NPE */
    private val track: (String, Map<String, Any>) -> Unit = { name, props -> trackEvent(name, props) },
) {
    // 串行化所有收藏写入：连点两次、以及与 sync 的并发，都不允许交错读-改-写同一份存储
    private val mutex = Mutex()

    init {
        settings.migrateFavoritesStorageIfNeeded()
    }

    /**
     * 当前应展示的收藏列表：登录态读云端镜像，否则读本地那份——**按登录态二选一，不做合并**。
     *
     * authState 用 cold flow 延迟到订阅时才读 [authProvider]，与 WhileSubscribed 配合，
     * 天然跟上 Activity 重建后的实例替换。
     */
    val favorites: StateFlow<List<FavoriteItem>> = combine(
        flow { emitAll(authProvider().authState) },
        settings.localFavorites,
        settings.cachedFavorites,
    ) { state, local, cached ->
        if (state is AuthState.LoggedIn) cached else local
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 列表页判断「这条是否已收藏」用；由 [favorites] 派生，全 app 只解析一次存储。 */
    val favoriteUrls: StateFlow<Set<String>> = favorites
        .map { items -> items.mapTo(mutableSetOf()) { it.url } }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val _errors = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** 收藏/取消失败（登录态下网络不可用等），由根部宿主统一提示。 */
    val errors: SharedFlow<Unit> = _errors.asSharedFlow()

    /** 收藏 / 取消收藏（按当前是否已收藏自动取反）。传入完整快照，取消时只用到 url。 */
    fun toggle(item: FavoriteItem) {
        scope.launch {
            mutex.withLock {
                if (currentFavorites().any { it.url == item.url }) removeLocked(item.url) else addLocked(item)
            }
        }
    }

    /**
     * 登录后 / 启动时的同步：先把匿名期攒下的收藏一次性导入，再用服务端全量刷新镜像。
     * 网络异常一律吞掉——本地态保持不变，下次进来重来。
     */
    suspend fun sync() {
        mutex.withLock {
            val token = authProvider().getAccessToken() ?: return
            runCatching {
                val pending = settings.currentLocalFavorites()
                if (pending.isNotEmpty()) {
                    // 导入失败就此打住：此时不能刷新镜像，否则登录态读到的镜像里没有这批条目，
                    // 用户会以为匿名期的收藏丢了（其实还在 local 键里等下次导入）
                    if (!api.batchPutFavorites(token, pending.map { withExternalId(it) })) return@runCatching
                    settings.setLocalFavorites(emptyList())
                }
                settings.setCachedFavorites(api.fetchFavorites(token))
            }
        }
    }

    /**
     * 触发一次同步，跑在仓库自有 [scope] 上（**不绑定任何 Compose composition**）。
     * 必须用它、而非在 composable 的 LaunchedEffect 里直接 await [sync]——否则用户登录后
     * 一旦离开当前屏（如登录发生在账户页、随即进「我的收藏」），composition 退出会取消协程
     * （LeftCompositionCancellationException），[sync] 半途中断，云端收藏拉不下来。
     */
    fun requestSync() {
        scope.launch { sync() }
    }

    /** 登出清理：只清云端镜像，匿名期那份不动（见 [SettingsManager.clearFavoritesOnSignOut]）。 */
    fun onSignOut() {
        settings.clearFavoritesOnSignOut()
    }

    // ---- 内部 ----

    private fun isLoggedIn(): Boolean = authProvider().authState.value is AuthState.LoggedIn

    /**
     * 当前收藏的同步快照。[favorites] 是 StateFlow，首个值要等订阅后才到，
     * 进屏即读（如收藏列表页的曝光埋点）会拿到初始空列表，故直接读存储。
     */
    fun currentFavorites(): List<FavoriteItem> =
        if (isLoggedIn()) settings.currentCachedFavorites() else settings.currentLocalFavorites()

    private suspend fun addLocked(item: FavoriteItem) {
        val entry = withExternalId(item)
        track("favorite_toggle", mapOf("on" to true, "source" to entry.source))
        if (!isLoggedIn()) {
            settings.setLocalFavorites(listOf(entry) + settings.currentLocalFavorites().filterNot { it.url == entry.url })
            return
        }
        val before = settings.currentCachedFavorites()
        settings.setCachedFavorites(listOf(entry) + before.filterNot { it.url == entry.url }) // 乐观
        val token = authProvider().getAccessToken()
        val ok = token != null && runCatching { api.putFavorite(token, entry) }.getOrDefault(false)
        if (!ok) {
            settings.setCachedFavorites(before)
            _errors.tryEmit(Unit)
        }
    }

    private suspend fun removeLocked(url: String) {
        track("favorite_toggle", mapOf("on" to false))
        if (!isLoggedIn()) {
            settings.setLocalFavorites(settings.currentLocalFavorites().filterNot { it.url == url })
            return
        }
        val before = settings.currentCachedFavorites()
        val target = before.firstOrNull { it.url == url } ?: return
        settings.setCachedFavorites(before.filterNot { it.url == url }) // 乐观
        val token = authProvider().getAccessToken()
        val ok = token != null &&
            runCatching { api.deleteFavorite(token, target.source, withExternalId(target).externalId) }.getOrDefault(false)
        if (!ok) {
            settings.setCachedFavorites(before)
            _errors.tryEmit(Unit)
        }
    }

    /**
     * 服务端要求 external_id 非空。列表页 / 解读页给的条目都自带（来自 API），
     * 缺失的只有 0.22.0 之前存下的存量收藏——一律用 url 顶上即可：
     * 该字段在收藏这条链路上只做用户内唯一键，从不与 contents join，无需反解真实 id。
     * 已登录时镜像来自服务端 GET，删除用的是服务端给的值，故存量行也一定删得掉。
     */
    private fun withExternalId(item: FavoriteItem): FavoriteItem =
        if (item.externalId.isBlank()) item.copy(externalId = item.url) else item
}

/** 全局单例：注入全局 SettingsManager / TrendingApi，登录态每次现取。 */
val globalFavoriteRepository by lazy {
    FavoriteRepository(settings = globalSettingsManager, api = TrendingApi())
}
