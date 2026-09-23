package whl.trending.ai.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds
import whl.trending.ai.auth.AuthManager
import whl.trending.ai.auth.AuthState
import whl.trending.ai.auth.globalAuthManager
import whl.trending.ai.core.AccountLink
import whl.trending.ai.data.local.LastDataCache
import whl.trending.ai.data.local.SettingsManager
import whl.trending.ai.data.local.globalLastDataCache
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.data.model.MeUser
import whl.trending.ai.data.model.QuotaResponse
import whl.trending.ai.data.repository.UserRepository

// 等登录态落定的上限，见 awaitResolvedAuthState
private val AUTH_RESOLVE_TIMEOUT = 3.seconds

/** 账户 Hub 上次数据缓存的 key：只存 [MeUser]，GitHub 数据归子页（[GithubProfileCache]） */
internal const val ACCOUNT_CACHE_KEY = "account"

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
)

/** 账户 Hub：登录态、`/api/me`、额度。不碰 GitHub——那些归 [GithubProfileViewModel]。 */
class ProfileViewModel(
    private val repository: UserRepository = UserRepository(),
    private val authManager: () -> AuthManager = { globalAuthManager },
    settingsManager: SettingsManager = globalSettingsManager,
    private val cache: LastDataCache = globalLastDataCache,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    /**
     * 额度卡 ⓘ 弹窗内容，未拉到过 app-config 为 null（不显示入口）。跟着缓存与语言走：
     * VM 常驻 Activity，升级后首启用户可能先进账户页、配置后落盘，读一次就会永久错过。
     */
    val quotaHelp: StateFlow<QuotaHelpContent?> =
        combine(settingsManager.quotaHelp, settingsManager.uiLanguageFlow, ::resolveQuotaHelp)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 进行中的整页加载协程；重复进入页面时先取消，避免两个 load 并发交叉写 state */
    private var loadJob: Job? = null
    /** 进行中的余额拉取协程；每次重拉先取消在途请求，避免两次调用乱序返回时旧值覆盖新值 */
    private var quotaJob: Job? = null

    /**
     * 是否已成功加载过当前账号的数据。VM 是 Activity 级缓存，切 tab 再回来时 [load] 据此跳过重拉。
     * 登出（authState→LoggedOut）时复位，确保换账号后重新加载。
     */
    private var hasLoaded = false

    init {
        // 监听登录态变化，实时反映到 Hub（Hub 常驻可达，登录/登出可能在停留期间发生）。
        // 只对「转变」反应，跳过初始发射——首帧加载交给 Screen 的 load()，避免双重加载。
        viewModelScope.launch {
            var prev: AuthState? = null
            authManager().authState.collect { state ->
                val previous = prev
                prev = state
                // 跳过初始发射，以及 Unknown→已知 的首次落定：后者不是登录态转变，只是答案揭晓，
                // 当成转变会与 Screen 的首帧 load() 撞成双重加载
                if (previous == null || previous is AuthState.Unknown || previous == state) return@collect
                when (state) {
                    AuthState.Unknown -> Unit // 落定后不会再回到未知
                    is AuthState.LoggedOut -> {
                        hasLoaded = false
                        loadJob?.cancel()
                        // 落回匿名态（非 loading，展示登录引导）而非 ProfileUiState() 的首屏 loading；
                        // 只补拉匿名档额度，不走整页 load()（无需 fetchMe）
                        _uiState.value = ProfileUiState(isLoading = false, loggedIn = false)
                        cache.remove(ACCOUNT_CACHE_KEY)
                        cache.remove(GithubProfileCache.KEY)
                        reloadQuota()
                    }
                    is AuthState.LoggedIn -> {
                        hasLoaded = false
                        load()
                    }
                }
            }
        }

        // 关联 GitHub 成功：身份变了但登录态没变，authState 不会发射，只能靠这个信号。
        viewModelScope.launch {
            AccountLink.linked.collect {
                hasLoaded = false
                load()
            }
        }
    }

    /**
     * 等登录态从 [AuthState.Unknown] 落定。Hub 对匿名用户可达，所以「不是 LoggedIn 就按匿名收尾」
     * 这个判断一旦提前生效，冷启动直奔账户页的登录用户会被摆上登录引导。
     * 正常是毫秒级：restore 只读一次本地存储。
     *
     * 超时兜底防的不是某个已知 bug，而是**这个等待无界**——落不了定就永远转圈，没有崩溃、
     * 没有日志、没有提示。超时把那类静默硬故障降级成「按未登录处理」，用户点一下就能重试。
     */
    private suspend fun awaitResolvedAuthState(): AuthState =
        withTimeoutOrNull(AUTH_RESOLVE_TIMEOUT) {
            authManager().authState.first { it !is AuthState.Unknown }
        } ?: AuthState.LoggedOut

    fun load() {
        // 余额每次进页都拉实时值（独立协程，不受下方跳过逻辑影响）：
        // 聊天消耗发生在页面之外，跳过整页重载时余额仍需刷新
        reloadQuota()
        val current = _uiState.value
        if (hasLoaded && current.user != null && !current.isError) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            // SWR：有缓存身份区秒出 + 顶部指示器自动刷新
            val cached = cache.get<MeUser>(ACCOUNT_CACHE_KEY)
            if (cached != null) {
                _uiState.value = _uiState.value.copy(isLoading = false, loggedIn = true, user = cached, isError = false)
                hasLoaded = true
                refreshInternal()
                return@launch
            }

            // 整页状态只改自己的字段：loadQuota 与本协程并行，quota 先到时整对象重建会把它抹掉
            _uiState.value = _uiState.value.copy(isLoading = true, isError = false)
            if (awaitResolvedAuthState() !is AuthState.LoggedIn) {
                // 未登录是 Hub 的正常态（展示登录引导 + 匿名额度，后者由上面的 reloadQuota 拉取）
                _uiState.value = _uiState.value.copy(isLoading = false, loggedIn = false, user = null)
                hasLoaded = true
                return@launch
            }
            val user = try {
                repository.fetchMe()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.value = _uiState.value.copy(isLoading = false, isError = true, loggedIn = true)
                return@launch
            }
            _uiState.value = _uiState.value.copy(isLoading = false, loggedIn = true, user = user)
            hasLoaded = true
            cache.put(ACCOUNT_CACHE_KEY, user)
        }
    }

    /** 下拉刷新：保留当前内容可见（不切首屏 loading），与 [load] 共享取消语义。 */
    fun refresh() {
        loadJob?.cancel()
        reloadQuota()
        loadJob = viewModelScope.launch {
            refreshInternal()
        }
    }

    /**
     * 拉取 credits 余额：与整页加载解耦，失败只降级配额卡（保留旧值时不置错误态）。
     * quota 不进缓存——余额要新鲜，SWR 快照对它是误导。
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
        if (awaitResolvedAuthState() !is AuthState.LoggedIn) {
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
        _uiState.value = _uiState.value.copy(isRefreshing = false, loggedIn = true, user = user)
        cache.put(ACCOUNT_CACHE_KEY, user)
    }

    fun signOut() = authManager().signOut()
}
