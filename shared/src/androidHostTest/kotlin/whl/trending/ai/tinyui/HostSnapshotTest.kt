package whl.trending.ai.tinyui

import app.tinyui.HostSnapshot
import java.io.File
import app.tinyui.Session
import kotlin.test.Test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 宿主契约没变，或变了且加了 HOST_VERSION；快照的 tinyui 行是下限，只判兼容（tinyui docs/updates.md §4.1）。
 * `./gradlew :shared:testAndroidHostTest -Ptinyui.updateHostSnapshot` 改为写入快照。
 */
class HostSnapshotTest {
    @Test
    fun hostMatchesTheSnapshotOfItsHostVersion() {
        val file = File("tinyui-host/$HOST_VERSION.txt")
        if (System.getProperty("tinyui.updateHostSnapshot") != null) {
            file.parentFile.mkdirs()
            file.writeText(HostSnapshot.render(testHost, HOST_VERSION))
            return
        }
        val hint = "HOST_VERSION $HOST_VERSION 还没合入 main 就加 -Ptinyui.updateHostSnapshot 重跑，否则先把它加 1 再重跑"
        assertTrue(file.exists(), "没有 ${file.path}；$hint")
        assertEquals(emptyList(), HostSnapshot.problems(file.readText(), testHost, HOST_VERSION), "宿主契约与 ${file.path} 不一致；$hint")
    }
}

/** 与 TrendingTinyUI.host 同一份契约，状态源是假的 */
private val testHost = TrendingTinyUI.build(MutableStateFlow("en"), MutableStateFlow(Session.LoggedOut), dataDir = null)
