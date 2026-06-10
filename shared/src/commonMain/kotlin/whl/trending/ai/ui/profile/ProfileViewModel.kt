package whl.trending.ai.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import whl.trending.ai.auth.AuthManager
import whl.trending.ai.auth.globalAuthManager
import whl.trending.ai.data.model.MeUser
import whl.trending.ai.data.repository.UserRepository

data class ProfileUiState(
    val isLoading: Boolean = true,
    val user: MeUser? = null,
    val isError: Boolean = false,
)

class ProfileViewModel(
    private val repository: UserRepository = UserRepository(),
    private val authManager: AuthManager = globalAuthManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = ProfileUiState(isLoading = true)
            val token = authManager.getAccessToken()
            if (token == null) {
                _uiState.value = ProfileUiState(isLoading = false, isError = true)
                return@launch
            }
            try {
                val user = repository.fetchMe(token)
                _uiState.value = ProfileUiState(isLoading = false, user = user)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.value = ProfileUiState(isLoading = false, isError = true)
            }
        }
    }

    fun signOut() = authManager.signOut()
}
