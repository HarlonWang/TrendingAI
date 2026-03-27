package whl.trending.ai.ui.main

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import whl.trending.ai.data.local.SettingsManager
import whl.trending.ai.data.model.TrendingMetadata
import whl.trending.ai.data.model.TrendingRepo
import whl.trending.ai.data.model.TrendingResponse
import whl.trending.ai.data.remote.TrendingApi
import whl.trending.ai.data.repository.TrendingRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var settingsManager: SettingsManager

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        settingsManager = SettingsManager(MapSettings())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    class FakeTrendingApi(
        private val responseDelay: Long = 0,
        private var shouldFail: Boolean = false
    ) : TrendingApi() {
        var callCount = 0

        override suspend fun fetchTrending(
            period: String,
            language: String,
            summaryLang: String,
            date: String?,
            batch: String?
        ): TrendingResponse {
            callCount++
            if (responseDelay > 0) delay(responseDelay)
            if (shouldFail) throw Exception("Network Error")
            
            return TrendingResponse(
                data = listOf(TrendingRepo(repoName = "Repo for $period")),
                metadata = TrendingMetadata(since = period)
            )
        }
    }

    @Test
    fun testInitialFetchSuccess() = runTest {
        val fakeApi = FakeTrendingApi()
        val repo = TrendingRepository(fakeApi)
        val viewModel = MainViewModel(repo, settingsManager)

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(1, viewModel.uiState.value.repos.size)
        assertEquals("Repo for daily", viewModel.uiState.value.repos[0].repoName)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun testFetchError() = runTest {
        val fakeApi = FakeTrendingApi(shouldFail = true)
        val repo = TrendingRepository(fakeApi)
        val viewModel = MainViewModel(repo, settingsManager)

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.repos.isEmpty())
        assertNotNull(viewModel.uiState.value.error)
        assertEquals("Network Error", viewModel.uiState.value.error)
    }

    @Test
    fun testConcurrencyRaceCondition() = runTest {
        val fakeApi = FakeTrendingApi(responseDelay = 1000)
        val repo = TrendingRepository(fakeApi)
        val viewModel = MainViewModel(repo, settingsManager)

        advanceTimeBy(100) 
        // Initial fetch started but suspended
        
        viewModel.updateFilter("weekly", "all")
        
        advanceUntilIdle()

        // Should have called API twice (initial and update)
        assertEquals(2, fakeApi.callCount)
        assertEquals("weekly", viewModel.uiState.value.selectedPeriod)
        assertEquals("Repo for weekly", viewModel.uiState.value.repos[0].repoName)
    }

    @Test
    fun testRefreshState() = runTest {
        val fakeApi = FakeTrendingApi(responseDelay = 1000)
        val repo = TrendingRepository(fakeApi)
        val viewModel = MainViewModel(repo, settingsManager)
        advanceUntilIdle()

        viewModel.fetchData(isRefresh = true)
        advanceTimeBy(100)
        
        assertTrue(viewModel.uiState.value.isRefreshing)
        assertFalse(viewModel.uiState.value.isLoading)

        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isRefreshing)
    }
}
