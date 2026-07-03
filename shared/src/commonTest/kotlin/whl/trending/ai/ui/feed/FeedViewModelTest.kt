package whl.trending.ai.ui.feed

import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.ObservableSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import whl.trending.ai.data.local.AppLanguage
import whl.trending.ai.data.local.FakeCacheFileStore
import whl.trending.ai.data.local.LastDataCache
import whl.trending.ai.data.local.SettingsManager
import whl.trending.ai.data.model.FeedItem
import whl.trending.ai.data.model.FeedResponse
import whl.trending.ai.data.repository.TrendingRepository

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelTest {

    private class FakeRepo(
        val onFeed: suspend () -> FeedResponse,
    ) : TrendingRepository() {
        override suspend fun getFeed(source: String, summaryLang: String): FeedResponse = onFeed()
    }

    private fun item(title: String) = FeedItem(source = "hackernews", externalId = title, title = title)

    private fun response(vararg titles: String) =
        FeedResponse(success = true, count = titles.size, data = titles.map(::item))

    private fun settings(): SettingsManager =
        SettingsManager(MapSettings() as ObservableSettings).also { it.setLanguage(AppLanguage.CHINESE) }

    private fun TestScope.cache(store: FakeCacheFileStore) =
        LastDataCache(store, StandardTestDispatcher(testScheduler))

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun cacheHitShowsCachedDataAndTriggersRefresh() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = FakeCacheFileStore()
        val cache = cache(store)
        cache.put("feed_hackernews_zh", response("cached"))
        val gate = CompletableDeferred<FeedResponse>()

        val vm = FeedViewModel("hackernews", FakeRepo { gate.await() }, settings(), cache)
        advanceUntilIdle()

        // 缓存先出，且自动刷新中（顶部指示器），不走骨架屏
        val mid = vm.uiState.value
        assertEquals(listOf("cached"), mid.items.map { it.title })
        assertFalse(mid.isLoading)
        assertTrue(mid.isRefreshing)

        gate.complete(response("fresh"))
        advanceUntilIdle()

        val end = vm.uiState.value
        assertEquals(listOf("fresh"), end.items.map { it.title })
        assertFalse(end.isRefreshing)
        // 新数据覆盖缓存
        assertEquals(response("fresh"), cache.get("feed_hackernews_zh"))
    }

    @Test
    fun noCacheKeepsSkeletonThenWritesCache() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = FakeCacheFileStore()
        val cache = cache(store)
        val gate = CompletableDeferred<FeedResponse>()

        val vm = FeedViewModel("hackernews", FakeRepo { gate.await() }, settings(), cache)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isLoading)

        gate.complete(response("fresh"))
        advanceUntilIdle()

        assertEquals(listOf("fresh"), vm.uiState.value.items.map { it.title })
        assertEquals(response("fresh"), cache.get("feed_hackernews_zh"))
    }

    @Test
    fun refreshFailureKeepsCachedContentSilently() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = FakeCacheFileStore()
        val cache = cache(store)
        cache.put("feed_hackernews_zh", response("cached"))

        val vm = FeedViewModel("hackernews", FakeRepo { throw RuntimeException("boom") }, settings(), cache)
        advanceUntilIdle()

        val end = vm.uiState.value
        // 有内容时刷新失败：静默保留，不显示整页错误
        assertEquals(listOf("cached"), end.items.map { it.title })
        assertNull(end.error)
        assertFalse(end.isRefreshing)
        // 失败不碰缓存
        assertEquals(response("cached"), cache.get("feed_hackernews_zh"))
    }

    @Test
    fun failureWithoutCacheShowsError() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = FeedViewModel(
            "hackernews",
            FakeRepo { throw RuntimeException("boom") },
            settings(),
            cache(FakeCacheFileStore()),
        )
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.error)
        assertFalse(vm.uiState.value.isLoading)
    }
}
