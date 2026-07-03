package whl.trending.ai.ui.trending

import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.ObservableSettings
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import whl.trending.ai.data.local.AppLanguage
import whl.trending.ai.data.local.FakeCacheFileStore
import whl.trending.ai.data.local.LastDataCache
import whl.trending.ai.data.local.SettingsManager
import whl.trending.ai.data.model.TrendingMetadata
import whl.trending.ai.data.model.TrendingRepo
import whl.trending.ai.data.model.TrendingResponse
import whl.trending.ai.data.repository.TrendingRepository

@OptIn(ExperimentalCoroutinesApi::class)
class TrendingViewModelTest {

    private class FakeRepo(
        val onTrending: suspend (period: String, language: String) -> TrendingResponse,
    ) : TrendingRepository() {
        override suspend fun getTrending(
            period: String,
            language: String,
            summaryLang: String,
            date: String?,
            batch: String?,
        ): TrendingResponse = onTrending(period, language)
    }

    private fun response(repoName: String) = TrendingResponse(
        success = true,
        count = 1,
        metadata = TrendingMetadata(since = "daily", capturedAt = "2026-07-03T00:17:00Z"),
        data = listOf(TrendingRepo(rank = 1, author = "a", repoName = repoName)),
    )

    private fun settings(): SettingsManager =
        SettingsManager(MapSettings() as ObservableSettings).also { it.setLanguage(AppLanguage.CHINESE) }

    private fun TestScope.cache(store: FakeCacheFileStore) =
        LastDataCache(store, StandardTestDispatcher(testScheduler))

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun cacheHitOnDefaultViewShowsCachedThenRefreshes() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val cache = cache(FakeCacheFileStore())
        cache.put("trending_default_zh", response("cached-repo"))

        val vm = TrendingViewModel(FakeRepo { _, _ -> response("fresh-repo") }, settings(), cache = cache)
        advanceUntilIdle()

        val end = vm.uiState.value
        assertEquals(listOf("fresh-repo"), end.repos.map { it.repoName })
        assertFalse(end.isRefreshing)
        assertEquals(response("fresh-repo"), cache.get("trending_default_zh"))
    }

    @Test
    fun nonDefaultViewFetchDoesNotOverwriteCache() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val cache = cache(FakeCacheFileStore())

        val vm = TrendingViewModel(
            FakeRepo { period, _ -> response("$period-repo") },
            settings(),
            cache = cache,
        )
        advanceUntilIdle()
        // 默认视图成功 → 写缓存
        assertEquals(response("daily-repo"), cache.get("trending_default_zh"))

        vm.updateFilter(period = "weekly", language = "all")
        advanceUntilIdle()
        // 非默认视图成功 → 展示 weekly，但缓存仍是默认视图快照
        assertEquals(listOf("weekly-repo"), vm.uiState.value.repos.map { it.repoName })
        assertEquals(response("daily-repo"), cache.get("trending_default_zh"))
    }

    @Test
    fun refreshFailureKeepsCachedContentSilently() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val cache = cache(FakeCacheFileStore())
        cache.put("trending_default_zh", response("cached-repo"))

        val vm = TrendingViewModel(FakeRepo { _, _ -> throw RuntimeException("boom") }, settings(), cache = cache)
        advanceUntilIdle()

        val end = vm.uiState.value
        assertEquals(listOf("cached-repo"), end.repos.map { it.repoName })
        assertNull(end.error)
        assertFalse(end.isRefreshing)
    }

    @Test
    fun cacheHitShowsRefreshingIndicatorWhileFetching() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val cache = cache(FakeCacheFileStore())
        cache.put("trending_default_zh", response("cached-repo"))
        val gate = kotlinx.coroutines.CompletableDeferred<TrendingResponse>()

        val vm = TrendingViewModel(FakeRepo { _, _ -> gate.await() }, settings(), cache = cache)
        advanceUntilIdle()

        val mid = vm.uiState.value
        assertEquals(listOf("cached-repo"), mid.repos.map { it.repoName })
        assertFalse(mid.isLoading)
        assertTrue(mid.isRefreshing)

        gate.complete(response("fresh-repo"))
        advanceUntilIdle()
        assertEquals(listOf("fresh-repo"), vm.uiState.value.repos.map { it.repoName })
    }
}
