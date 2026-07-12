package whl.trending.ai.ui.picks

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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import whl.trending.ai.data.local.SummaryLanguage
import whl.trending.ai.data.local.FakeCacheFileStore
import whl.trending.ai.data.local.LastDataCache
import whl.trending.ai.data.local.SettingsManager
import whl.trending.ai.data.model.PickItem
import whl.trending.ai.data.model.PicksMetadata
import whl.trending.ai.data.model.PicksResponse
import whl.trending.ai.data.repository.TrendingRepository

@OptIn(ExperimentalCoroutinesApi::class)
class PicksViewModelTest {

    private class FakeRepo(
        val onPicks: suspend () -> PicksResponse,
    ) : TrendingRepository() {
        override suspend fun getPicks(summaryLang: String): PicksResponse = onPicks()
    }

    private fun response(date: String) = PicksResponse(
        success = true,
        metadata = PicksMetadata(date = date),
        speedRead = listOf(PickItem(rank = 1, title = "item-$date")),
    )

    private fun settings(): SettingsManager =
        SettingsManager(MapSettings() as ObservableSettings).also { it.setSummaryLanguage(SummaryLanguage.CHINESE) }

    private fun TestScope.cache(store: FakeCacheFileStore) =
        LastDataCache(store, StandardTestDispatcher(testScheduler))

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun cacheHitShowsCachedThenOverwritesWithFresh() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val cache = cache(FakeCacheFileStore())
        cache.put("picks_zh", response("2026-07-02"))
        val gate = CompletableDeferred<PicksResponse>()

        val vm = PicksViewModel(FakeRepo { gate.await() }, settings(), cache)
        advanceUntilIdle()

        val mid = vm.uiState.value
        assertEquals("2026-07-02", mid.picks?.metadata?.date)
        assertFalse(mid.isLoading)
        assertTrue(mid.isRefreshing)

        gate.complete(response("2026-07-03"))
        advanceUntilIdle()

        assertEquals("2026-07-03", vm.uiState.value.picks?.metadata?.date)
        assertEquals(response("2026-07-03"), cache.get("picks_zh"))
    }

    @Test
    fun refreshFailureKeepsCachedContentSilently() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val cache = cache(FakeCacheFileStore())
        cache.put("picks_zh", response("2026-07-02"))

        val vm = PicksViewModel(FakeRepo { throw RuntimeException("boom") }, settings(), cache)
        advanceUntilIdle()

        val end = vm.uiState.value
        assertEquals("2026-07-02", end.picks?.metadata?.date)
        assertNull(end.error)
        assertFalse(end.isRefreshing)
        assertEquals(response("2026-07-02"), cache.get("picks_zh"))
    }
}
