package whl.trending.ai.update

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

interface UpdateChecker {
    val isEnabled: Boolean
    val isChecking: StateFlow<Boolean>
    val isUpToDate: StateFlow<Boolean>
    val isCheckFailed: StateFlow<Boolean>
    fun manualCheck()
}

object NoOpUpdateChecker : UpdateChecker {
    override val isEnabled = false
    override val isChecking = MutableStateFlow(false)
    override val isUpToDate = MutableStateFlow(false)
    override val isCheckFailed = MutableStateFlow(false)
    override fun manualCheck() {}
}

/**
 * 由渠道的 UpdateWrapper 在组合期间安装。做成 Compose 状态而不是普通 var：
 * 根上的 rememberAppSettings 在首次组合就读 isEnabled 决定要不要「检查更新」行，那时安装还没发生；
 * 普通 var 的后续赋值不会触发重组，apk 渠道会永远少这一行。
 */
var globalUpdateChecker: UpdateChecker by mutableStateOf(NoOpUpdateChecker)
