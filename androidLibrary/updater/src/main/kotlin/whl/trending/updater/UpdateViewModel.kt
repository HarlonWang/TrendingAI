package whl.trending.updater

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import whl.trending.ai.core.platform.getAppVersion
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.update.UpdateChecker
import whl.trending.ai.update.isWhatsNewPending

class UpdateViewModel : ViewModel(), UpdateChecker {

    override val isEnabled = true

    private val api = UpdateApi()

    private val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L

    private val _isChecking = MutableStateFlow(false)
    override val isChecking: StateFlow<Boolean> = _isChecking

    private val _isUpToDate = MutableStateFlow(false)
    override val isUpToDate: StateFlow<Boolean> = _isUpToDate

    private val _isCheckFailed = MutableStateFlow(false)
    override val isCheckFailed: StateFlow<Boolean> = _isCheckFailed

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo

    init {
        autoCheck()
    }

    private fun autoCheck() {
        // 升级后首启会弹 What's New，本次启动跳过自动检查，避免双弹窗叠加（下次启动恢复）
        if (isWhatsNewPending()) return
        val lastCheck = globalSettingsManager.getLastUpdateCheckTime()
        if (System.currentTimeMillis() - lastCheck < CHECK_INTERVAL_MS) return
        doCheck()
    }

    override fun manualCheck() {
        doCheck()
    }

    private fun doCheck() = viewModelScope.launch {
        if (_isChecking.value) return@launch
        _isChecking.value = true
        _isUpToDate.value = false
        _isCheckFailed.value = false

        val latest = withContext(Dispatchers.IO) { api.fetchLatestRelease() }
        // 网络请求已结束，先收起 loading——否则「已是最新」展示态会被 AboutScreen 里
        // 优先级更高的 isChecking 分支持续遮蔽（该分支的 delay 期间 isChecking 仍为 true）
        _isChecking.value = false

        if (latest != null) {
            globalSettingsManager.setLastUpdateCheckTime(System.currentTimeMillis())
            val current = getAppVersion()
            if (isNewerVersion(latest.tagName, current)) {
                // 更新内容取自 release asset，任一环节失败仅回退为不带内容的弹窗，不影响更新提示本身
                val whatsNew = withContext(Dispatchers.IO) {
                    latest.whatsNewAssetUrl?.let { api.fetchWhatsNew(it) }
                }
                _updateInfo.value = UpdateInfo(
                    latestVersion = latest.tagName,
                    currentVersion = current,
                    whatsNew = acceptWhatsNew(whatsNew, latest.tagName),
                )
            } else {
                _isUpToDate.value = true
                delay(2000)
                _isUpToDate.value = false
            }
        } else {
            // 网络/解析失败：给出可感知的失败提示，避免 loading 消失后一片空白
            _isCheckFailed.value = true
            delay(2000)
            _isCheckFailed.value = false
        }
    }

    fun dismissUpdate() {
        _updateInfo.value = null
    }
}

internal fun isNewerVersion(latest: String, current: String): Boolean {
    fun parse(v: String) = v.substringBefore("-")
        .split(".").mapNotNull { it.toIntOrNull() }
    val l = parse(latest)
    val c = parse(current)
    for (i in 0 until maxOf(l.size, c.size)) {
        val diff = l.getOrElse(i) { 0 } - c.getOrElse(i) { 0 }
        if (diff != 0) return diff > 0
    }
    // 数字部分相等时按 semver 处理预发布后缀：正式版 > 同号预发布版（0.22.0 > 0.22.0-beta.1），
    // 保证灰度用户在转正后能收到更新提示；latest 来自 releases/latest、不会是预发布版，
    // 预发布之间的先后无需比较
    return "-" in current && "-" !in latest
}
