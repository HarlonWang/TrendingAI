package whl.trending.ai.data.repository

import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.ObservableSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import whl.trending.ai.data.local.SettingsManager
import whl.trending.ai.data.model.FavoriteItem
import whl.trending.ai.data.model.PendingFavoriteOp
import whl.trending.ai.data.remote.TrendingApi
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 内存版 TrendingApi：记录调用并维护一份「服务端」收藏，键为 source|externalId。 */
private class FakeApi : TrendingApi() {
    val server = LinkedHashMap<String, FavoriteItem>()
    var getCount = 0
    var batchCount = 0
    val puts = mutableListOf<FavoriteItem>()
    val deletes = mutableListOf<Pair<String, String>>()
    var failNextBatch = false

    private fun key(source: String, externalId: String) = "$source|$externalId"

    fun seedServer(vararg items: FavoriteItem) {
        items.forEach { server[key(it.source, it.externalId)] = it }
    }

    override suspend fun fetchFavorites(): List<FavoriteItem> {
        getCount++
        return server.values.toList()
    }

    override suspend fun putFavorite(item: FavoriteItem): Boolean {
        puts += item
        server[key(item.source, item.externalId)] = item
        return true
    }

    override suspend fun deleteFavorite(source: String, externalId: String): Boolean {
        deletes += source to externalId
        server.remove(key(source, externalId))
        return true
    }

    override suspend fun batchPutFavorites(items: List<FavoriteItem>): Boolean {
        if (failNextBatch) { failNextBatch = false; return false }
        batchCount++
        items.forEach { server[key(it.source, it.externalId)] = it }
        return true
    }
}

class FavoriteRepositoryTest {
    private lateinit var settings: SettingsManager
    private lateinit var api: FakeApi

    private fun repo(loggedIn: Boolean = true) =
        FavoriteRepository(settings, api, loggedIn = { loggedIn })

    private fun gh(name: String, ext: String = name) = FavoriteItem(
        url = "https://github.com/$name",
        title = name,
        source = "github",
        savedAt = 1000,
        externalId = ext,
    )

    @BeforeTest
    fun setup() {
        settings = SettingsManager(MapSettings() as ObservableSettings)
        api = FakeApi()
    }

    @Test
    fun 首次登录合并_本地全部batch上云并用服务端覆盖本地() = runTest {
        settings.replaceFavorites(listOf(gh("a/b"), gh("c/d")))
        api.seedServer(gh("e/f")) // 云端已有的另一条

        repo().sync()

        assertEquals(1, api.batchCount)
        assertTrue(settings.favoritesMerged())
        // 本地被服务端全量覆盖：合并后含云端已有 + 本地上推的三条
        val local = settings.currentFavorites().map { it.externalId }.toSet()
        assertEquals(setOf("a/b", "c/d", "e/f"), local)
    }

    @Test
    fun 首次合并_存量收藏无externalId时github从url回填() = runTest {
        // 存量本地收藏：externalId 为空
        settings.replaceFavorites(listOf(FavoriteItem(url = "https://github.com/foo/bar", title = "foo/bar", source = "github")))

        repo().sync()

        // batch 上推的条目 externalId 已回填为 owner/repo
        assertEquals("foo/bar", api.server.keys.first().removePrefix("github|"))
    }

    @Test
    fun batch失败_不置merged也不覆盖本地() = runTest {
        settings.replaceFavorites(listOf(gh("a/b")))
        api.failNextBatch = true

        repo().sync()

        assertFalse(settings.favoritesMerged())
        assertEquals(0, api.getCount) // 未走到 GET
        assertEquals(listOf("a/b"), settings.currentFavorites().map { it.externalId }) // 本地保留
    }

    @Test
    fun 已合并_删除跨设备传播_GET覆盖移除本地条目() = runTest {
        // 已完成首次合并；本地有 X，云端已被别的设备删掉（服务端为空）
        settings.setFavoritesMerged(true)
        settings.replaceFavorites(listOf(gh("a/b")))
        // api.server 为空

        repo().sync()

        assertEquals(0, api.batchCount) // 已合并，不再 batch
        assertTrue(settings.currentFavorites().isEmpty()) // X 被服务端全量覆盖移除
    }

    @Test
    fun 已合并_在途pending的add在GET覆盖时被保留() = runTest {
        settings.setFavoritesMerged(true)
        // 服务端空，但本地有一条尚未 flush 成功的 add op
        settings.setPendingFavoriteOps(
            listOf(PendingFavoriteOp("add", "https://github.com/x/y", "github", "x/y", gh("x/y")))
        )

        repo().sync()

        // GET 返回空，但 pending add 叠加回来，本地仍含 x/y
        assertEquals(listOf("x/y"), settings.currentFavorites().map { it.externalId })
    }

    @Test
    fun 已合并_flush先推增量op再GET() = runTest {
        settings.setFavoritesMerged(true)
        settings.replaceFavorites(listOf(gh("a/b")))
        settings.setPendingFavoriteOps(
            listOf(PendingFavoriteOp("add", "https://github.com/a/b", "github", "a/b", gh("a/b")))
        )

        repo().sync()

        assertEquals(1, api.puts.size) // pending add 被 flush
        assertTrue(settings.getPendingFavoriteOps().isEmpty()) // flush 成功后出队
    }

    @Test
    fun 匿名时sync直接返回不动本地() = runTest {
        settings.replaceFavorites(listOf(gh("a/b")))

        repo(loggedIn = false).sync()

        assertFalse(settings.favoritesMerged())
        assertEquals(0, api.getCount)
        assertEquals(0, api.batchCount)
        assertEquals(listOf("a/b"), settings.currentFavorites().map { it.externalId })
    }

    // 注：add()/remove() 的手势路径会触发 content_action 埋点（eventbase），
    // 在纯 JVM host-test 中未初始化会 NPE，故不在此单测；其本地写入 + 入队逻辑
    // 由上面的「在途 pending 保留」「flush 增量」用例间接覆盖（构造同一 PendingFavoriteOp 形态）。

    // 回归：requestSync 必须在仓库自有 scope 上把同步跑到底（拉取并覆盖本地），
    // 而非绑定调用方（Compose composition）——否则登录后切屏会取消协程、拉取半途中断。
    @Test
    fun requestSync_在独立scope上跑完整拉取覆盖() = runTest {
        settings.setFavoritesMerged(true)
        settings.replaceFavorites(emptyList())
        api.seedServer(gh("a/b"), gh("c/d"))
        val repo = FavoriteRepository(settings, api, loggedIn = { true }, scope = CoroutineScope(StandardTestDispatcher(testScheduler)))

        repo.requestSync()
        advanceUntilIdle()

        assertEquals(setOf("a/b", "c/d"), settings.currentFavorites().map { it.externalId }.toSet())
    }

    @Test
    fun requestSync_匿名时不动本地() = runTest {
        settings.setFavoritesMerged(true)
        settings.replaceFavorites(listOf(gh("x/y")))
        val repo = FavoriteRepository(settings, api, loggedIn = { false }, scope = CoroutineScope(StandardTestDispatcher(testScheduler)))

        repo.requestSync()
        advanceUntilIdle()

        assertEquals(0, api.getCount)
        assertEquals(listOf("x/y"), settings.currentFavorites().map { it.externalId })
    }

    @Test
    fun onSignOut_清空收藏与同步状态() = runTest {
        settings.setFavoritesMerged(true)
        settings.replaceFavorites(listOf(gh("a/b")))
        settings.setPendingFavoriteOps(listOf(PendingFavoriteOp("delete", "u", "github", "a/b", null)))

        repo().onSignOut()

        assertTrue(settings.currentFavorites().isEmpty())
        assertTrue(settings.getPendingFavoriteOps().isEmpty())
        assertFalse(settings.favoritesMerged())
    }
}
