package whl.trending.ai.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import whl.trending.ai.auth.AuthManager
import whl.trending.ai.auth.AuthState
import whl.trending.ai.auth.FollowingInfo
import whl.trending.ai.auth.FollowingProvider
import whl.trending.ai.auth.GithubTokenLookup
import whl.trending.ai.auth.GithubTokenProvider
import whl.trending.ai.auth.OwnRepoEventsProvider
import whl.trending.ai.auth.globalAuthManager
import whl.trending.ai.core.AccountLink
import whl.trending.ai.data.local.LastDataCache
import whl.trending.ai.data.local.SettingsManager
import whl.trending.ai.data.local.globalLastDataCache
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.data.model.ContributionCalendar
import whl.trending.ai.data.remote.GithubApi
import whl.trending.ai.data.remote.GithubUser

private const val FEED_PAGE_SIZE = 30
private const val FEED_MAX_EVENTS = 300 // GitHub received_events 硬上限
private const val HIGHLIGHTS_MIN_PER_LOAD = 10   // 精选档单次调用至少累计新增条目
private const val MAX_PAGES_PER_LOAD = 5          // 单次调用最多连续拉取页数（防止过久）

data class GithubProfileUiState(
    /** 本地记下的 GitHub 用户名（`/api/me` 落盘）；null = 未关联，页面无从加载 */
    val login: String? = null,
    val isRefreshing: Boolean = false,
    /** GitHub 档案；token 不可用或请求失败时为 null（头部只显示 @login，隐藏计数行） */
    val githubUser: GithubUser? = null,
    /** 最近一年贡献日历；加载中或不可用时为 null（UI 隐藏热力图） */
    val contributions: ContributionCalendar? = null,
    val feedItems: List<GithubFeedItem> = emptyList(),
    val isFeedLoading: Boolean = false,
    val feedEndReached: Boolean = false,
    /** feed 不可用（无 GitHub token / 请求失败） */
    val feedUnavailable: Boolean = false,
    /** 服务端明确没存 token（vault 被清空过）：feed 区换成重新关联引导 */
    val githubTokenMissing: Boolean = false,
    /** true = 精选档（默认），false = 全部档 */
    val highlightsOnly: Boolean = true,
) {
    /**
     * feed 是否应显示加载态：首个结果尚未产出（空、且未到底、未不可用）即视为加载中，
     * 而不仅是「正在发起网络请求」——这样进页到首批 feed 到达期间始终有 loading，不会先空白。
     */
    val isFeedLoadingVisible: Boolean
        get() = isFeedLoading || (feedItems.isEmpty() && !feedEndReached && !feedUnavailable)
}

/**
 * GitHub 子页的数据：档案、贡献图、关注、自有仓库事件、动态流。**只在进子页时请求**——
 * 账户 Hub 不碰 GitHub。
 *
 * VM 是 Activity 级常驻（nav3 未挂 VM 装饰器），数据作废时页面未必可见，所以作废只重置并
 * 递增 [reloadKey]，由页面按它触发 [load]，不在这里直接拉。
 */
class GithubProfileViewModel(
    private val githubApi: GithubApi = GithubApi(),
    private val tokenProvider: GithubTokenProvider = GithubTokenProvider.shared,
    private val followingProvider: FollowingProvider = FollowingProvider.shared,
    private val ownRepoEventsProvider: OwnRepoEventsProvider = OwnRepoEventsProvider.shared,
    private val authManager: () -> AuthManager = { globalAuthManager },
    private val settingsManager: SettingsManager = globalSettingsManager,
    private val cache: LastDataCache = globalLastDataCache,
) : ViewModel() {
    private val _uiState = MutableStateFlow(GithubProfileUiState(highlightsOnly = settingsManager.currentFeedHighlightsOnly()))
    val uiState: StateFlow<GithubProfileUiState> = _uiState.asStateFlow()

    private val _reloadKey = MutableStateFlow(0)
    val reloadKey: StateFlow<Int> = _reloadKey.asStateFlow()

    private var nextFeedPage = 1
    /** 已消费的原始 events 总数（用于判断是否到达 GitHub 300 条硬上限） */
    private var consumedRawCount = 0
    /** 进行中的 feed 拉取协程；切档/重载前先取消，避免旧档结果写回新档 state */
    private var feedLoadJob: Job? = null
    private var loadJob: Job? = null

    private var followingInfo: FollowingInfo? = null
    /** 规则3：我的仓库上别人的 star/fork（精选档合流） */
    private var ownRepoItems: List<GithubFeedItem> = emptyList()

    /** 关注列表与自有仓库事件已就绪；此前页面触底分页会按缺失的数据过滤 */
    private var feedPrepared = false

    /** 已加载过当前账号：下钻 followers/following/repos 再返回时 [load] 据此跳过重拉 */
    private var hasLoaded = false

    init {
        viewModelScope.launch {
            var prev: AuthState? = null
            authManager().authState.collect { state ->
                val previous = prev
                prev = state
                // 只对真实的登录态转变作废；Unknown→已知 只是答案揭晓
                if (previous == null || previous is AuthState.Unknown || previous == state) return@collect
                invalidate()
            }
        }
        // 关联 / 重新关联 GitHub 成功：身份或 token 变了，登录态却没变
        viewModelScope.launch {
            AccountLink.linked.collect { invalidate() }
        }
    }

    private fun invalidate() {
        loadJob?.cancel()
        feedLoadJob?.cancel()
        hasLoaded = false
        feedPrepared = false
        _uiState.value = GithubProfileUiState(highlightsOnly = settingsManager.currentFeedHighlightsOnly())
        _reloadKey.value++
    }

    private suspend fun persistSnapshot() {
        val snapshot = GithubProfileCache.from(_uiState.value, cache.get(GithubProfileCache.KEY)) ?: return
        cache.put(GithubProfileCache.KEY, snapshot)
    }

    fun load() {
        if (hasLoaded) return
        loadJob?.cancel()
        feedLoadJob?.cancel()
        loadJob = viewModelScope.launch {
            val highlightsOnly = settingsManager.currentFeedHighlightsOnly()
            val login = settingsManager.currentGithubLogin()
            if (login == null) {
                _uiState.value = GithubProfileUiState(highlightsOnly = highlightsOnly, feedUnavailable = true)
                return@launch
            }
            // SWR：有缓存整页秒出 + 顶部指示器自动刷新；feed 与档位绑定，档位不一致不复用
            val cached = cache.get<GithubProfileCache>(GithubProfileCache.KEY)?.takeIf { it.login == login }
            _uiState.value = GithubProfileUiState(
                login = login,
                isRefreshing = cached != null,
                githubUser = cached?.githubUser,
                contributions = cached?.contributions,
                feedItems = cached?.takeIf { it.highlightsOnly == highlightsOnly }?.feedItems.orEmpty(),
                highlightsOnly = highlightsOnly,
            )
            hasLoaded = true
            fetchAll(login)
        }
    }

    /** 下拉刷新：保留当前内容可见，连关注列表与自有仓库事件的会话缓存一起重拉 */
    fun refresh() {
        val login = _uiState.value.login ?: return load()
        loadJob?.cancel()
        feedLoadJob?.cancel()
        followingProvider.clear()
        ownRepoEventsProvider.clear()
        _uiState.value = _uiState.value.copy(isRefreshing = true)
        loadJob = viewModelScope.launch { fetchAll(login) }
    }

    private suspend fun fetchAll(login: String) = coroutineScope {
        feedPrepared = false
        nextFeedPage = 1
        consumedRawCount = 0
        followingInfo = null
        ownRepoItems = emptyList()

        val githubToken = when (val lookup = tokenProvider.lookup()) {
            is GithubTokenLookup.Available -> lookup.token
            GithubTokenLookup.Missing, GithubTokenLookup.Failed -> {
                _uiState.value = _uiState.value.copy(
                    isRefreshing = false,
                    feedUnavailable = true,
                    githubTokenMissing = lookup == GithubTokenLookup.Missing,
                )
                hasLoaded = false // 下次进页重试
                return@coroutineScope
            }
        }
        val githubUser = try {
            githubApi.fetchUser(githubToken)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null // 档案失败不致命，feed 继续尝试
        }
        // 旧 feed 保留到此刻才清空，随后重新填充，避免下拉时列表闪空
        _uiState.value = _uiState.value.copy(
            isRefreshing = false,
            githubUser = githubUser ?: _uiState.value.githubUser,
            githubTokenMissing = false,
            feedItems = emptyList(),
            feedEndReached = false,
            feedUnavailable = false,
        )
        persistSnapshot()

        // 贡献热力图：与 feed 并行拉取，失败保留旧值；挂在 loadJob 下，随作废一起取消
        launch {
            try {
                val calendar = githubApi.fetchContributionCalendar(githubToken, login)
                _uiState.value = _uiState.value.copy(contributions = calendar)
                persistSnapshot()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            }
        }

        // 关注列表失败降级为 null：精选档退化为不看关注关系
        followingInfo = followingProvider.get()

        val loginLower = login.lowercase()
        ownRepoItems = ownRepoEventsProvider.get().orEmpty()
            .map { it.toFeedItem() }
            .filter { item ->
                (item.kind == GithubFeedKind.STARRED || item.kind == GithubFeedKind.FORKED) &&
                    item.actorLogin.lowercase() != loginLower
            }
            .map { item ->
                item.copy(
                    kind = if (item.kind == GithubFeedKind.STARRED)
                        GithubFeedKind.STARRED_YOUR_REPO
                    else
                        GithubFeedKind.FORKED_YOUR_REPO
                )
            }
            .distinctBy { it.id }

        feedPrepared = true
        loadMoreFeed()
    }

    fun loadMoreFeed() {
        val state = _uiState.value
        if (!feedPrepared || state.isFeedLoading || state.feedEndReached || state.feedUnavailable) return
        val login = state.login ?: return
        feedLoadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isFeedLoading = true)
            val githubToken = tokenProvider.get()
            if (githubToken == null) {
                _uiState.value = _uiState.value.copy(isFeedLoading = false, feedUnavailable = true)
                return@launch
            }
            try {
                val highlightsOnly = _uiState.value.highlightsOnly
                var pagesThisLoad = 0
                var newItemsThisLoad = 0
                var endReached = false
                // 局部累积：进入循环时读一次 state，之后只在局部 merge，
                // 每轮写回一次（渐进展示）；配合 Job 取消杜绝跨档交叉读写
                var currentItems = _uiState.value.feedItems

                // 循环拉页：精选档过滤后新增不足 10 条且未到底时继续拉
                while (!endReached && pagesThisLoad < MAX_PAGES_PER_LOAD) {
                    val events = githubApi.fetchReceivedEvents(githubToken, login, nextFeedPage, FEED_PAGE_SIZE)
                    pagesThisLoad++
                    consumedRawCount += events.size

                    val filtered = events.map { it.toFeedItem() }.let { items ->
                        if (highlightsOnly) items.filter { it.isHighlight(followingInfo) }
                        else items
                    }

                    val merged = (currentItems + filtered).distinctBy { it.id }
                    newItemsThisLoad += merged.size - currentItems.size
                    currentItems = merged

                    endReached = events.size < FEED_PAGE_SIZE || consumedRawCount >= FEED_MAX_EVENTS
                    nextFeedPage++

                    // 精选档合流 ownRepoItems（按时间倒序，去重）
                    val display = if (highlightsOnly) {
                        (currentItems + ownRepoItems)
                            .sortedByDescending { it.createdAt }
                            .distinctBy { it.id }
                    } else {
                        currentItems
                    }

                    _uiState.value = _uiState.value.copy(
                        feedItems = display,
                        feedEndReached = endReached,
                    )

                    if (newItemsThisLoad >= HIGHLIGHTS_MIN_PER_LOAD) break
                }

                _uiState.value = _uiState.value.copy(isFeedLoading = false)
                persistSnapshot()
            } catch (e: Exception) {
                // 取消时直接透传，不写 state——isFeedLoading 由取消方的状态重置兜底归 false
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.value = _uiState.value.copy(
                    isFeedLoading = false,
                    feedUnavailable = _uiState.value.feedItems.isEmpty(),
                )
            }
        }
    }

    /** 切换精选/全部档：取消进行中的拉取，持久化设置，重置 feed 状态并重新拉取第一页 */
    fun setFeedFilter(highlightsOnly: Boolean) {
        feedLoadJob?.cancel()
        settingsManager.setFeedHighlightsOnly(highlightsOnly)
        nextFeedPage = 1
        consumedRawCount = 0
        _uiState.value = _uiState.value.copy(
            feedItems = emptyList(),
            isFeedLoading = false,
            feedEndReached = false,
            feedUnavailable = !feedPrepared,
            highlightsOnly = highlightsOnly,
        )
        loadMoreFeed()
    }
}
