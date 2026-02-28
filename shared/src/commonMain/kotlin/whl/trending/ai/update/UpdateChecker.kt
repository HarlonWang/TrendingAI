package whl.trending.ai.update

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface UpdateChecker {
    val isChecking: StateFlow<Boolean>
    val isUpToDate: StateFlow<Boolean>
    fun manualCheck()
}

object NoOpUpdateChecker : UpdateChecker {
    override val isChecking = MutableStateFlow(false)
    override val isUpToDate = MutableStateFlow(false)
    override fun manualCheck() {}
}

var globalUpdateChecker: UpdateChecker = NoOpUpdateChecker
