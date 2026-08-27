package whl.trending.ai.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import whl.trending.ai.auth.AuthManager
import whl.trending.ai.auth.AuthState
import whl.trending.ai.auth.FollowingInfo
import whl.trending.ai.auth.FollowingProvider
import whl.trending.ai.auth.GithubTokenProvider
import whl.trending.ai.auth.OwnRepoEventsProvider
import whl.trending.ai.auth.globalAuthManager
import whl.trending.ai.core.AccountLink
import whl.trending.ai.data.local.LastDataCache
import whl.trending.ai.data.local.SettingsManager
import whl.trending.ai.data.local.globalLastDataCache
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.data.model.ContributionCalendar
import whl.trending.ai.data.model.MeUser
import whl.trending.ai.data.model.QuotaResponse
import whl.trending.ai.data.remote.GithubApi
import whl.trending.ai.data.remote.GithubUser
import whl.trending.ai.data.repository.UserRepository

private const val FEED_PAGE_SIZE = 30
private const val FEED_MAX_EVENTS = 300 // GitHub received_events 硬上限
private const val HIGHLIGHTS_MIN_PER_LOAD = 10   // 精选档单次调用至少累计新增条目
private const val MAX_PAGES_PER_LOAD = 5          // 单次调用最多连续拉取页数（防止过久）

data class ProfileUiState(
    val isLoading: Boolean = true,
    /** 下拉刷新中：与首屏 [isLoading] 区分，刷新时保留旧内容、仅显示下拉指示器 */
    val isRefreshing: Boolean = false,
    val user: MeUser? = null,
    /**
     * 是否已登录。账户 Hub 对未登录用户同样可达（展示登录引导 + 匿名额度 + 设置项），
     * 故 [user] == null 有两种含义：未登录（loggedIn=false，正常匿名态）
     * 或登录态加载失败（[isError]=true）。UI 据此区分「登录引导」与「重试」。
     */
    val loggedIn: Boolean = false,
    val isError: Boolean = false,
    /** credits 余额（账户页配额卡）；加载中/失败为 null，失败态由 [quotaError] 区分 */
    val quota: QuotaResponse? = null,
    /** quota 拉取失败且无旧值可展示：配额卡显示错误占位，不影响页面其余部分 */
    val quotaError: Boolean = false,
    /** GitHub 实时计数；token 不可用或请求失败时为 null（UI 隐藏计数行） */
    val githubUser: GithubUser? = null,
    /** 最近一年贡献日历；加载中或不可用时为 null（UI 隐藏热力图） */
    val contributions: ContributionCalendar? = null,
    val feedItems: List<GithubFeedItem> = emptyList(),
    val isFeedLoading: Boolean = false,
    val feedEndReached: Boolean = false,
    /** feed 不可用（无 GitHub token / 请求失败），与整页 isError 区分 */
    val feedUnavailable: Boolean = false,
    /** true = 精选档（默认），false = 全部档 */
    val highlightsOnly: Boolean = true,
) {
    /**
     * feed 是否应显示加载态。语义：feed 首个结果尚未产出（空、且未到底、未不可用）即视为加载中，
     * 而不仅是「正在发起网络请求」(isFeedLoading)——这样从页面内容出现到首批 feed 到达期间始终有
     * loading，不会先空白。整页加载中 (isLoading) 时不计入，避免与整页 loading 叠加。
     */
    val isFeedLoadingVisible: Boolean
        get() = !isLoading &&
            (isFeedLoading || (feedItems.isEmpty() && !feedEndReached && !feedUnavailable))
}

class ProfileViewModel(
    private val repository: UserRepository = UserRepository(),
    private val githubApi: GithubApi = GithubApi(),
    private val tokenProvider: GithubTokenProvider = GithubTokenProvider.shared,
    private val followingProvider: FollowingProvider = FollowingProvider.shared,
    private val ownRepoEventsProvider: OwnRepoEventsProvider = OwnRepoEventsProvider.shared,
    private val authManager: () -> AuthManager = { globalAuthManager },
    private val settingsManager: SettingsManager = globalSettingsManager,
    private val cache: LastDataCache = globalLastDataCache,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private var nextFeedPage = 1
    /** 已消费的原始 events 总数（用于判断是否到达 GitHub 300 条硬上限） */
    private var consumedRawCount = 0
    /** 进行中的 feed 拉取协程；切档/重载前先取消，避免旧档结果写回新档 state */
    private var feedLoadJob: Job? = null
    /** 进行中的整页加载协程；重复进入页面时先取消，避免两个 load 并发交叉写 state 与分页游标 */
    private var loadJob: Job? = null
    /** 进行中的余额拉取协程；每次重拉先取消在途请求，避免两次调用乱序返回时旧值覆盖新值 */
    private var quotaJob: Job? = null

    /** 缓存当次会话的关注列表（load() 时重置） */
    private var followingInfo: FollowingInfo? = null
    /** 规则3：我的仓库上别人的 star/fork（精选档合流，load() 时重置） */
    private var ownRepoItems: List<GithubFeedItem> = emptyList()

    /**
     * 是否已成功加载过当前账号的数据。VM 是 Activity 级缓存，下钻 followers/following/repos
     * 子页再返回时 [load] 据此跳过重拉（数据仍在 state 里），避免无谓的整页重载。
     * 登出（authState→LoggedOut）时复位，确保换账号后重新加载。
     */
    private var hasLoaded = false

    init {
        // 监听登录态变化，实时反映到 Hub（Hub 常驻可达，登录/登出可能在停留期间发生）：
        // - 登出：清空缓存与档案，复位后以匿名态重载（拉匿名额度、显示登录引导）；
        // - 登录：复位 hasLoaded 后重载，拉 /api/me 与真实额度。
        // 只对「转变」反应，跳过初始发射——首帧加载交给 Screen 的 load()，避免双重加载。
        viewModelScope.launch {
            var prev: AuthState? = null
            authManager().authState.collect { state ->
                val changed = prev != null && prev != state
                prev = state
                if (!changed) return@collect
                when (state) {
                    is AuthState.LoggedOut -> {
                        hasLoaded = false
                        loadJob?.cancel()
                        feedLoadJob?.cancel()
                        // 落回匿名态（非 loading，展示登录引导）而非 ProfileUiState() 的首屏 loading；
                        // 只补拉匿名档额度，不走整页 load()（无需 fetchMe，token 此时应为 null）
                        _uiState.value = ProfileUiState(isLoading = false, loggedIn = false)
                        cache.remove(ProfileCache.KEY)
                        reloadQuota()
                    }
                    is AuthState.LoggedIn -> {
                        hasLoaded = false
                        load()
                    }
                    is AuthState.LoggingIn -> Unit
                }
            }
        }

        // 关联 GitHub 成功：身份变了但登录态没变，authState 不会发射，只能靠这个信号。
        // MainActivity 在 ON_RESUME 里已用 fresh 请求刷过服务端缓存，这里普通重载即可拿到新身份。
        viewModelScope.launch {
            AccountLink.linked.collect {
                hasLoaded = false
                load()
            }
        }
    }

    /**
     * 落盘当前 state 快照（覆盖 + 只增不减，见 [ProfileCache.from]）。
     * 调用时机：fetchMe 成功、contributions 到达、loadMoreFeed 每轮写回 state 后。
     */
    private suspend fun persistSnapshot() {
        val snapshot = ProfileCache.from(_uiState.value, cache.get(ProfileCache.KEY)) ?: return
        cache.put(ProfileCache.KEY, snapshot)
    }

    fun load() {
        // 余额每次进页都拉实时值（独立协程，不受下方跳过逻辑影响）：
        // 聊天消耗发生在页面之外，跳过整页重载时余额仍需刷新
        reloadQuota()
        // 已加载过且数据健在（非错误态）则跳过：用于从子页返回时不重拉
        val current = _uiState.value
        if (hasLoaded && current.user != null && !current.isError) return
        loadJob?.cancel()
        feedLoadJob?.cancel()
        loadJob = viewModelScope.launch {
            val highlightsOnly = settingsManager.currentFeedHighlightsOnly()

            // 整页重建期间保留 quota 字段：loadQuota 与本协程并行，quota 先到时若被下面的
            // 整对象重建抹掉，配额卡会静默消失（登出重登后首次进页必现的竞态）。
            // 读取放在写入时刻而非提前快照，尽量窄化与 loadQuota 写入交错的窗口。
            fun freshState(build: (quota: QuotaResponse?, quotaError: Boolean) -> ProfileUiState) {
                val s = _uiState.value
                _uiState.value = build(s.quota, s.quotaError)
            }

            // SWR：有缓存整页秒出（header/计数/热力图/feed）+ 顶部指示器自动刷新
            val cached = cache.get<ProfileCache>(ProfileCache.KEY)
            if (cached != null) {
                freshState { quota, quotaError ->
                    ProfileUiState(
                        isLoading = false,
                        loggedIn = true,
                        user = cached.user,
                        githubUser = cached.githubUser,
                        contributions = cached.contributions,
                        // feed 与档位绑定，档位不一致时不复用（只用 header 部分，feed 走加载态）
                        feedItems = if (cached.highlightsOnly == highlightsOnly) cached.feedItems else emptyList(),
                        highlightsOnly = highlightsOnly,
                        quota = quota,
                        quotaError = quotaError,
                    )
                }
                hasLoaded = true
                refreshInternal()
                return@launch
            }

            freshState { quota, quotaError ->
                ProfileUiState(isLoading = true, highlightsOnly = highlightsOnly, quota = quota, quotaError = quotaError)
            }
            nextFeedPage = 1
            consumedRawCount = 0
            followingInfo = null
            ownRepoItems = emptyList()
            if (authManager().authState.value !is AuthState.LoggedIn) {
                // 未登录是 Hub 的正常态（展示登录引导 + 匿名额度，后者由上面的 reloadQuota 拉取）
                freshState { quota, quotaError ->
                    ProfileUiState(isLoading = false, loggedIn = false, highlightsOnly = highlightsOnly, quota = quota, quotaError = quotaError)
                }
                hasLoaded = true
                return@launch
            }
            val user = try {
                repository.fetchMe()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                freshState { quota, quotaError ->
                    ProfileUiState(isLoading = false, isError = true, loggedIn = true, highlightsOnly = highlightsOnly, quota = quota, quotaError = quotaError)
                }
                return@launch
            }
            freshState { quota, quotaError ->
                ProfileUiState(isLoading = false, loggedIn = true, user = user, highlightsOnly = highlightsOnly, quota = quota, quotaError = quotaError)
            }
            hasLoaded = true
            persistSnapshot()
            loadGithubData(user)
        }
    }

    private suspend fun loadGithubData(user: MeUser) {
        val githubToken = tokenProvider.get()
        val login = user.githubLogin
        if (githubToken == null || login == null) {
            _uiState.value = _uiState.value.copy(feedUnavailable = true)
            return
        }
        try {
            val githubUser = githubApi.fetchUser(githubToken)
            _uiState.value = _uiState.value.copy(githubUser = githubUser)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            // 计数失败不致命，feed 继续尝试
        }

        // 贡献热力图：与 feed 并行拉取，失败仅隐藏，不影响 feed
        viewModelScope.launch {
            try {
                val calendar = githubApi.fetchContributionCalendar(githubToken, login)
                _uiState.value = _uiState.value.copy(contributions = calendar)
                persistSnapshot()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            }
        }

        // 拉取关注列表（失败降级为 null，保留旧行为）
        followingInfo = followingProvider.get()

        // 规则3：自有仓库上别人的 star/fork（会话级缓存，限最近活跃的前 N 个仓库）
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

        loadMoreFeed()
    }

    fun loadMoreFeed() {
        val state = _uiState.value
        if (state.isFeedLoading || state.feedEndReached || state.feedUnavailable) return
        val login = state.user?.githubLogin ?: return
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

                    // 已累计到足够条目则停止本次循环
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

    /**
     * 下拉刷新：保留当前 header/feed 可见（不切首屏 loading），重置分页游标后整体重载。
     * 与 [load] 共享取消语义，避免与在途加载交叉写 state。
     */
    fun refresh() {
        loadJob?.cancel()
        feedLoadJob?.cancel()
        reloadQuota()
        loadJob = viewModelScope.launch {
            refreshInternal()
        }
    }

    /**
     * 拉取 credits 余额：与整页加载解耦，失败只降级配额卡（保留旧值时不置错误态），
     * 不影响档案与 feed。quota 不进 ProfileCache——余额要新鲜，SWR 快照对它是误导。
     * 自身串行：新请求先取消在途旧请求，避免两次调用乱序返回时旧值覆盖新值。
     */
    private fun reloadQuota() {
        quotaJob?.cancel()
        quotaJob = viewModelScope.launch { loadQuota() }
    }

    private suspend fun loadQuota() {
        // Hub 对未登录用户可达：带不带 Bearer 由鉴权插件按会话决定，服务端据此定档
        try {
            val quota = repository.fetchQuota()
            _uiState.value = _uiState.value.copy(quota = quota, quotaError = false)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            // 已有旧值时保留（stale-while-error），仅首次加载失败才显示错误占位
            _uiState.value = _uiState.value.copy(quotaError = _uiState.value.quota == null)
        }
    }

    /** 刷新主体：手动下拉与缓存命中后的自动刷新（SWR）共用 */
    private suspend fun refreshInternal() {
        _uiState.value = _uiState.value.copy(isRefreshing = true, isError = false)
        nextFeedPage = 1
        consumedRawCount = 0
        followingInfo = null
        ownRepoItems = emptyList()
        if (authManager().authState.value !is AuthState.LoggedIn) {
            // 未登录下拉刷新：额度由 refresh() 的 reloadQuota 刷新，这里落回匿名态，不报错
            _uiState.value = _uiState.value.copy(isRefreshing = false, loggedIn = false, user = null)
            return
        }
        val user = try {
            repository.fetchMe()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            _uiState.value = _uiState.value.copy(isRefreshing = false, isError = true)
            return
        }
        // 旧内容保留到此刻才清空 feed，随后由 loadGithubData 重新填充，避免下拉时列表闪空
        _uiState.value = _uiState.value.copy(
            isRefreshing = false,
            loggedIn = true,
            user = user,
            feedItems = emptyList(),
            feedEndReached = false,
            feedUnavailable = false,
            contributions = null,
        )
        persistSnapshot()
        loadGithubData(user)
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
            feedUnavailable = false,
            highlightsOnly = highlightsOnly,
        )
        loadMoreFeed()
    }

    fun signOut() = authManager().signOut()
}
