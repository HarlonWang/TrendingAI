package whl.trending.ai.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import whl.trending.ai.auth.AuthManager
import whl.trending.ai.auth.GithubTokenProvider
import whl.trending.ai.auth.globalAuthManager
import whl.trending.ai.data.local.SettingsManager
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.data.model.MeUser
import whl.trending.ai.data.remote.GithubApi
import whl.trending.ai.data.remote.GithubUser
import whl.trending.ai.data.repository.UserRepository

private const val FEED_PAGE_SIZE = 30
private const val FEED_MAX_EVENTS = 300 // GitHub received_events 硬上限
private const val HIGHLIGHTS_MIN_PER_LOAD = 10   // 精选档单次调用至少累计新增条目
private const val MAX_PAGES_PER_LOAD = 5          // 单次调用最多连续拉取页数（防止过久）

data class ProfileUiState(
    val isLoading: Boolean = true,
    val user: MeUser? = null,
    val isError: Boolean = false,
    /** GitHub 实时计数；token 不可用或请求失败时为 null（UI 隐藏计数行） */
    val githubUser: GithubUser? = null,
    val feedItems: List<GithubFeedItem> = emptyList(),
    val isFeedLoading: Boolean = false,
    val feedEndReached: Boolean = false,
    /** feed 不可用（无 GitHub token / 请求失败），与整页 isError 区分 */
    val feedUnavailable: Boolean = false,
    /** true = 精选档（默认），false = 全部档 */
    val highlightsOnly: Boolean = true,
)

class ProfileViewModel(
    private val repository: UserRepository = UserRepository(),
    private val githubApi: GithubApi = GithubApi(),
    private val tokenProvider: GithubTokenProvider = GithubTokenProvider.shared,
    private val authManager: () -> AuthManager = { globalAuthManager },
    private val settingsManager: SettingsManager = globalSettingsManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private var nextFeedPage = 1
    /** 已消费的原始 events 总数（用于判断是否到达 GitHub 300 条硬上限） */
    private var consumedRawCount = 0

    fun load() {
        viewModelScope.launch {
            val highlightsOnly = settingsManager.getFeedHighlightsOnlySync()
            _uiState.value = ProfileUiState(isLoading = true, highlightsOnly = highlightsOnly)
            nextFeedPage = 1
            consumedRawCount = 0
            val token = authManager().getAccessToken()
            if (token == null) {
                _uiState.value = ProfileUiState(isLoading = false, isError = true, highlightsOnly = highlightsOnly)
                return@launch
            }
            val user = try {
                repository.fetchMe(token)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.value = ProfileUiState(isLoading = false, isError = true, highlightsOnly = highlightsOnly)
                return@launch
            }
            _uiState.value = ProfileUiState(isLoading = false, user = user, highlightsOnly = highlightsOnly)
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
        loadMoreFeed()
    }

    fun loadMoreFeed() {
        val state = _uiState.value
        if (state.isFeedLoading || state.feedEndReached || state.feedUnavailable) return
        val login = state.user?.githubLogin ?: return
        viewModelScope.launch {
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

                // 循环拉页：精选档过滤后新增不足 10 条且未到底时继续拉
                while (!endReached && pagesThisLoad < MAX_PAGES_PER_LOAD) {
                    val events = githubApi.fetchReceivedEvents(githubToken, login, nextFeedPage, FEED_PAGE_SIZE)
                    pagesThisLoad++
                    consumedRawCount += events.size

                    val filtered = events.map { it.toFeedItem() }.let { items ->
                        if (highlightsOnly) items.filter { it.kind in HighlightFeedKinds && !it.isBot() }
                        else items
                    }

                    val existing = _uiState.value.feedItems
                    val merged = (existing + filtered).distinctBy { it.id }
                    newItemsThisLoad += merged.size - existing.size

                    endReached = events.size < FEED_PAGE_SIZE || consumedRawCount >= FEED_MAX_EVENTS
                    nextFeedPage++

                    _uiState.value = _uiState.value.copy(
                        feedItems = merged,
                        feedEndReached = endReached,
                    )

                    // 已累计到足够条目则停止本次循环
                    if (newItemsThisLoad >= HIGHLIGHTS_MIN_PER_LOAD) break
                }

                _uiState.value = _uiState.value.copy(isFeedLoading = false)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.value = _uiState.value.copy(
                    isFeedLoading = false,
                    feedUnavailable = _uiState.value.feedItems.isEmpty(),
                )
            }
        }
    }

    /** 切换精选/全部档：持久化设置，重置 feed 状态并重新拉取第一页 */
    fun setFeedFilter(highlightsOnly: Boolean) {
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
