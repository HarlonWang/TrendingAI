package whl.trending.ai.tinyui

import app.tinyui.HostSnapshot
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 宿主契约没变，或变了且加了 HOST_VERSION（tinyui docs/updates.md §4.1）。
 * `./gradlew :shared:testAndroidHostTest -Ptinyui.updateHostSnapshot` 改为写入快照。
 */
class HostSnapshotTest {
    @Test
    fun hostMatchesTheSnapshotOfItsHostVersion() {
        val file = File("tinyui-host/$HOST_VERSION.txt")
        val current = HostSnapshot.render(TrendingTinyUI.host, HOST_VERSION)
        if (System.getProperty("tinyui.updateHostSnapshot") != null) {
            file.parentFile.mkdirs()
            file.writeText(current)
            return
        }
        val hint = "HOST_VERSION $HOST_VERSION 还没随发版带出去就加 -Ptinyui.updateHostSnapshot 重跑，否则先把它加 1 再重跑"
        assertTrue(file.exists(), "没有 ${file.path}；$hint")
        assertEquals(file.readText(), current, "宿主契约与 ${file.path} 不一致；$hint")
    }
}
