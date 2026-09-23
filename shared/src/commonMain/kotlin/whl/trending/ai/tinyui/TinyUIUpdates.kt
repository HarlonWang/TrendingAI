package whl.trending.ai.tinyui

import app.tinyui.Bundle
import app.tinyui.updates.UpdateEvent
import app.tinyui.updates.Updates
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okio.Path
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.MissingResourceException
import trendingai.shared.generated.resources.Res
import whl.trending.ai.core.analytics.AppEvent
import whl.trending.ai.core.analytics.TinyUIUpdateOutcome
import whl.trending.ai.core.analytics.track
import whl.trending.ai.data.local.globalSettingsManager

/** Android filesDir/tinyui、iOS Application Support/tinyui */
internal expect fun tinyuiUpdatesDir(): Path

/** 页面包 `trendingai` 的热下发（tinyui docs/updates.md §4）；通道由设置里的隐藏开关决定 */
object TinyUIUpdates {
    const val PKG = "trendingai"
    private const val BASE_URL = "https://updates.tinyui.app/trendingai"

    private val state = MutableStateFlow<Updates?>(null)
    val updates: StateFlow<Updates?> = state

    private val http by lazy { HttpClient { expectSuccess = true } }

    /** App 启动即调，先于任何 TinyUI 页挂载：指针回滚要靠下一次 check 才送得到已装上的用户 */
    suspend fun start() {
        if (state.value != null) return
        val updates = withContext(Dispatchers.Default) {
            Updates(listOf(embedded()), HOST_VERSION, tinyuiUpdatesDir(), globalSettingsManager.getOrCreateInstallId(), ::fetch, ::report)
        }
        state.value = updates
        updates.check()
    }

    // 不随页面取消：切换通道后马上离开关于页，下载也要跑完
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 切换通道后调：按新通道下载，下次启动生效（进程内不换包，tinyui docs/updates.md §4.4） */
    fun checkNow() {
        scope.launch { state.value?.check() }
    }

    @OptIn(ExperimentalResourceApi::class)
    private suspend fun embedded(): Bundle = Bundle.load { path ->
        try { Res.readBytes("files/tinyui/$PKG/$path") } catch (e: MissingResourceException) { null }
    }

    private suspend fun fetch(path: String): ByteArray =
        http.get("$BASE_URL/${globalSettingsManager.currentTinyUIChannel()}/$path").readRawBytes()

    private fun report(event: UpdateEvent) {
        println("TinyUI updates $event")
        val tracked = when (event) {
            is UpdateEvent.Installed -> AppEvent.TinyUIUpdateChecked(TinyUIUpdateOutcome.INSTALLED, event.pkg, event.version)
            is UpdateEvent.Skipped -> AppEvent.TinyUIUpdateChecked(TinyUIUpdateOutcome.SKIPPED, event.pkg, event.version, event.reason.name)
            is UpdateEvent.Failed -> AppEvent.TinyUIUpdateChecked(TinyUIUpdateOutcome.FAILED, event.pkg, event.version, event.stage.name)
            is UpdateEvent.RolledBack -> AppEvent.TinyUIPageRolledBack(event.pkg, event.version, event.page, event.kind)
            is UpdateEvent.Running, is UpdateEvent.UpToDate -> null
        }
        tracked?.let(::track)
    }
}
