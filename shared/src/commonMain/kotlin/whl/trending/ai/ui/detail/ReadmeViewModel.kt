package whl.trending.ai.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import whl.trending.ai.auth.AuthManager
import whl.trending.ai.auth.RepoStarService
import whl.trending.ai.auth.globalAuthManager
import whl.trending.ai.data.repository.TrendingRepository

data class ReadmeUiState(
    val html: String = "",
    val isLoading: Boolean = true,
    val error: String? = null,
    /** 当前平台是否支持 GitHub 登录（不支持则隐藏 star 按钮） */
    val starSupported: Boolean = false,
    /** 是否已 star；null=未知/未登录（显示空心星，点击引导登录） */
    val isStarred: Boolean? = null,
    /** star/unstar 请求进行中（按钮位显示加载指示） */
    val isStarLoading: Boolean = false,
)

class ReadmeViewModel(
    private val owner: String,
    private val repo: String,
    private val repository: TrendingRepository = TrendingRepository(),
    private val starService: RepoStarService = RepoStarService.shared,
    private val authManager: () -> AuthManager = { globalAuthManager },
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReadmeUiState())
    val uiState: StateFlow<ReadmeUiState> = _uiState.asStateFlow()

    /** star 操作结果一次性事件：UI 收到后弹 Snackbar */
    private val _starEvents = MutableSharedFlow<RepoStarService.Result>(extraBufferCapacity = 1)
    val starEvents: SharedFlow<RepoStarService.Result> = _starEvents.asSharedFlow()

    init {
        fetchReadme()
        refreshStarState()
    }

    /** 进入页面时查询初始 star 状态（已登录才查；无 token 时保持 null=空心星） */
    private fun refreshStarState() {
        if (!authManager().isSupported) return
        _uiState.update { it.copy(starSupported = true) }
        viewModelScope.launch {
            val starred = starService.isStarred(owner, repo)
            _uiState.update { it.copy(isStarred = starred) }
        }
    }

    /** 切换 star 状态：请求完成后再更新图标（期间显示 loading），失败/需登录通过 [starEvents] 提示 */
    fun toggleStar() {
        val state = _uiState.value
        if (state.isStarLoading) return
        val currentlyStarred = state.isStarred == true
        viewModelScope.launch {
            _uiState.update { it.copy(isStarLoading = true) }
            val result =
                if (currentlyStarred) starService.unstar(owner, repo)
                else starService.star(owner, repo)
            when (result) {
                RepoStarService.Result.STARRED ->
                    _uiState.update { it.copy(isStarred = true, isStarLoading = false) }
                RepoStarService.Result.UNSTARRED ->
                    _uiState.update { it.copy(isStarred = false, isStarLoading = false) }
                RepoStarService.Result.NEED_LOGIN,
                RepoStarService.Result.FAILED ->
                    _uiState.update { it.copy(isStarLoading = false) }
            }
            _starEvents.tryEmit(result)
        }
    }

    fun signIn(source: String) = authManager().signIn(source)

    fun fetchReadme() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val response = repository.getReadme(owner, repo)
                if (response.success) {
                    _uiState.update { it.copy(html = response.html, isLoading = false) }
                } else {
                    _uiState.update { it.copy(error = response.error, isLoading = false) }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.update { it.copy(error = e.message ?: "Unknown Error", isLoading = false) }
            }
        }
    }
}
