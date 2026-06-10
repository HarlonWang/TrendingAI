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
import whl.trending.ai.data.model.MeUser
import whl.trending.ai.data.remote.GithubApi
import whl.trending.ai.data.remote.GithubUser
import whl.trending.ai.data.repository.UserRepository

private const val FEED_PAGE_SIZE = 30
private const val FEED_MAX_EVENTS = 300 // GitHub received_events 硬上限

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
)

class ProfileViewModel(
    private val repository: UserRepository = UserRepository(),
    private val githubApi: GithubApi = GithubApi(),
    private val tokenProvider: GithubTokenProvider = GithubTokenProvider.shared,
    private val authManager: AuthManager = globalAuthManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private var nextFeedPage = 1

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = ProfileUiState(isLoading = true)
            nextFeedPage = 1
            val token = authManager.getAccessToken()
            if (token == null) {
                _uiState.value = ProfileUiState(isLoading = false, isError = true)
                return@launch
            }
            val user = try {
                repository.fetchMe(token)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.value = ProfileUiState(isLoading = false, isError = true)
                return@launch
            }
            _uiState.value = ProfileUiState(isLoading = false, user = user)
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
                val events = githubApi.fetchReceivedEvents(githubToken, login, nextFeedPage, FEED_PAGE_SIZE)
                val newItems = events.map { it.toFeedItem() }
                val merged = (_uiState.value.feedItems + newItems).distinctBy { it.id }
                val endReached = events.size < FEED_PAGE_SIZE || merged.size >= FEED_MAX_EVENTS
                nextFeedPage++
                _uiState.value = _uiState.value.copy(
                    feedItems = merged,
                    isFeedLoading = false,
                    feedEndReached = endReached,
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.value = _uiState.value.copy(
                    isFeedLoading = false,
                    feedUnavailable = _uiState.value.feedItems.isEmpty(),
                )
            }
        }
    }

    fun signOut() = authManager.signOut()
}
