package whl.trending.ai.data.repository

import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.ObservableSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import whl.trending.ai.auth.AuthManager
import whl.trending.ai.auth.AuthState
import whl.trending.ai.auth.SignInMethod
import whl.trending.ai.data.local.SettingsManager
import whl.trending.ai.data.model.FavoriteItem
import whl.trending.ai.data.remote.TrendingApi
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 内存版 TrendingApi：记录调用并维护一份「服务端」收藏，键为 source|externalId。 */
private class FakeApi : TrendingApi() {
    val server = LinkedHashMap<String, FavoriteItem>()
    var getCount = 0
    var batchCount = 0
    val puts = mutableListOf<FavoriteItem>()
    val deletes = mutableListOf<Pair<String, String>>()
    var failWrites = false
    var failNextBatch = false

    private fun key(source: String, externalId: String) = "$source|$externalId"

    fun seedServer(vararg items: FavoriteItem) {
        items.forEach { server[key(it.source, it.externalId)] = it }
    }

    override suspend fun fetchFavorites(accessToken: String): List<FavoriteItem> {
        getCount++
        return server.values.toList()
    }

    override suspend fun putFavorite(accessToken: String, item: FavoriteItem): Boolean {
        if (failWrites) return false
        puts += item
        server[key(item.source, item.externalId)] = item
        return true
    }

    override suspend fun deleteFavorite(accessToken: String, source: String, externalId: String): Boolean {
        if (failWrites) return false
        deletes += source to externalId
        server.remove(key(source, externalId))
        return true
    }

    override suspend fun batchPutFavorites(accessToken: String, items: List<FavoriteItem>): Boolean {
        if (failNextBatch) { failNextBatch = false; return false }
        batchCount++
        items.forEach { server[key(it.source, it.externalId)] = it }
        return true
    }
}

/** 可切换登录态的 AuthManager；token 为 null 模拟已登录但取不到 token（离线）。 */
private class FakeAuth(loggedIn: Boolean, private val token: String? = "tok") : AuthManager {
    override val isSupported: Boolean = true
    private val state = MutableStateFlow<AuthState>(if (loggedIn) AuthState.LoggedIn else AuthState.LoggedOut)
    override val authState: StateFlow<AuthState> = state
    override fun signIn(source: String) {}
    override fun signIn(source: String, method: SignInMethod) {}
    override fun signOut() {}
    override suspend fun getAccessToken(): String? = token
}

class FavoriteRepositoryTest {
    private lateinit var settings: SettingsManager
    private lateinit var api: FakeApi

    private fun TestScope.repo(auth: AuthManager) = FavoriteRepository(
        settings = settings,
        api = api,
        authProvider = { auth },
        scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
        track = { _, _ -> },
    )

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
    fun 未登录_收藏与取消只落本地不打接口() = runTest {
        val repo = repo(FakeAuth(loggedIn = false))

        repo.toggle(gh("a/b"))
        advanceUntilIdle()
        assertEquals(listOf("a/b"), settings.currentLocalFavorites().map { it.externalId })
        assertEquals(0, api.puts.size)

        repo.toggle(gh("a/b")) // 再点一次 = 取消
        advanceUntilIdle()
        assertTrue(settings.currentLocalFavorites().isEmpty())
        assertEquals(0, api.deletes.size)
        assertTrue(settings.currentCachedFavorites().isEmpty()) // 未登录不碰镜像
    }

    @Test
    fun 登录后sync_匿名收藏一次性导入并清空本地键() = runTest {
        settings.setLocalFavorites(listOf(gh("a/b"), gh("c/d")))
        api.seedServer(gh("e/f")) // 云端已有的另一条

        repo(FakeAuth(loggedIn = true)).sync()

        assertEquals(1, api.batchCount)
        assertTrue(settings.currentLocalFavorites().isEmpty()) // 导入成功即清空，不会二次导入
        assertEquals(setOf("a/b", "c/d", "e/f"), settings.currentCachedFavorites().map { it.externalId }.toSet())
    }

    @Test
    fun 导入失败_不清本地也不刷新镜像() = runTest {
        settings.setLocalFavorites(listOf(gh("a/b")))
        api.failNextBatch = true

        repo(FakeAuth(loggedIn = true)).sync()

        assertEquals(listOf("a/b"), settings.currentLocalFavorites().map { it.externalId }) // 留着下次重来
        assertEquals(0, api.getCount) // 未走到 GET，避免登录态读到不含这批条目的镜像
    }

    @Test
    fun 存量收藏无externalId_导入时用url顶上() = runTest {
        settings.setLocalFavorites(
            listOf(FavoriteItem(url = "https://news.ycombinator.com/item?id=1", title = "x", source = "hackernews"))
        )

        repo(FakeAuth(loggedIn = true)).sync()

        assertEquals("hackernews|https://news.ycombinator.com/item?id=1", api.server.keys.single())
    }

    @Test
    fun 已登录_删除跨设备传播_GET刷新镜像移除条目() = runTest {
        settings.setCachedFavorites(listOf(gh("a/b"))) // 另一台设备已删，服务端为空

        repo(FakeAuth(loggedIn = true)).sync()

        assertEquals(0, api.batchCount) // 本地键为空，无需导入
        assertTrue(settings.currentCachedFavorites().isEmpty())
    }

    @Test
    fun 已登录_收藏成功_镜像与服务端一致() = runTest {
        val repo = repo(FakeAuth(loggedIn = true))

        repo.toggle(gh("a/b"))
        advanceUntilIdle()

        assertEquals(listOf("a/b"), settings.currentCachedFavorites().map { it.externalId })
        assertEquals(1, api.puts.size)
        assertTrue(settings.currentLocalFavorites().isEmpty()) // 登录态绝不写本地键
    }

    @Test
    fun 已登录_收藏失败_回滚镜像并发出错误() = runTest {
        api.failWrites = true
        val repo = repo(FakeAuth(loggedIn = true))
        val errors = mutableListOf<Unit>()
        val collector = CoroutineScope(StandardTestDispatcher(testScheduler))
        collector.launchCollect(repo, errors)

        repo.toggle(gh("a/b"))
        advanceUntilIdle()

        assertTrue(settings.currentCachedFavorites().isEmpty()) // 乐观写入已回滚
        assertEquals(1, errors.size)
        collector.cancel()
    }

    @Test
    fun 已登录_取消失败_条目回到镜像() = runTest {
        settings.setCachedFavorites(listOf(gh("a/b")))
        api.failWrites = true
        val repo = repo(FakeAuth(loggedIn = true))

        repo.toggle(gh("a/b"))
        advanceUntilIdle()

        assertEquals(listOf("a/b"), settings.currentCachedFavorites().map { it.externalId })
    }

    @Test
    fun 已登录但取不到token_视为失败不静默丢弃() = runTest {
        val repo = repo(FakeAuth(loggedIn = true, token = null))

        repo.toggle(gh("a/b"))
        advanceUntilIdle()

        assertTrue(settings.currentCachedFavorites().isEmpty())
        assertTrue(settings.currentLocalFavorites().isEmpty()) // 不能偷偷落到本地键：登录态读的是镜像，用户会看不见
    }

    @Test
    fun requestSync_在独立scope上跑完拉取() = runTest {
        api.seedServer(gh("a/b"), gh("c/d"))
        val repo = repo(FakeAuth(loggedIn = true))

        repo.requestSync()
        advanceUntilIdle()

        assertEquals(setOf("a/b", "c/d"), settings.currentCachedFavorites().map { it.externalId }.toSet())
    }

    @Test
    fun onSignOut_只清镜像不动匿名那份() = runTest {
        settings.setCachedFavorites(listOf(gh("a/b")))
        settings.setLocalFavorites(listOf(gh("x/y"))) // 尚未导入成功的匿名收藏

        repo(FakeAuth(loggedIn = true)).onSignOut()

        assertTrue(settings.currentCachedFavorites().isEmpty())
        assertEquals(listOf("x/y"), settings.currentLocalFavorites().map { it.externalId })
    }
}

/** 收集 errors 的小工具：避免在测试体里写一堆 launch/collect 噪音。 */
private fun CoroutineScope.launchCollect(repo: FavoriteRepository, sink: MutableList<Unit>) {
    launch { repo.errors.collect { sink += it } }
}
