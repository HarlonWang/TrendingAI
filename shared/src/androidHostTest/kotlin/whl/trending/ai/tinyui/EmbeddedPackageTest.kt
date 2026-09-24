package whl.trending.ai.tinyui

import app.tinyui.BuildManifest
import app.tinyui.PackageCheck
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Assume.assumeTrue

/**
 * 发版检查（tinyui docs/updates.md §4.1）：内置包能跑在本宿主上，且是给当前 HOST_VERSION 发布过的。
 * HOST_VERSION 刚加 1、JS 还没在新版本下发布时必然不过，所以只在 -Ptinyui.releaseCheck 时跑（发版脚本与 release.yml）。
 */
class EmbeddedPackageTest {
    @Test
    fun embeddedPackageRunsOnThisHost() {
        assumeTrue(System.getProperty("tinyui.releaseCheck") != null)
        val manifest = BuildManifest.parse(File("src/commonMain/composeResources/files/tinyui/${TinyUIUpdates.PKG}/manifest.json").readText())
        val refresh = "scripts/release-smoke.sh 会刷新内置包"
        assertEquals(emptyList(), PackageCheck.problems(manifest, TrendingTinyUI.host), "内置包 ${manifest.version} 不能跑在本宿主上；$refresh")
        assertEquals(
            HOST_VERSION, manifest.hostVersion,
            "内置包是给宿主版本 ${manifest.hostVersion} 发布的，当前 HOST_VERSION 是 $HOST_VERSION：等 JS 在 $HOST_VERSION 下发布并晋级 production；$refresh",
        )
    }
}
