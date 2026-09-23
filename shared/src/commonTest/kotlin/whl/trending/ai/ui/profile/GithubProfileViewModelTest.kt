package whl.trending.ai.ui.profile

import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.ObservableSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
import whl.trending.ai.auth.AuthManager
import whl.trending.ai.auth.AuthState
import whl.trending.ai.auth.FollowingInfo
import whl.trending.ai.auth.FollowingProvider
import whl.trending.ai.auth.GithubTokenLookup
import whl.trending.ai.auth.GithubTokenProvider
import whl.trending.ai.auth.OwnRepoEventsProvider
import whl.trending.ai.data.local.FakeCacheFileStore
import whl.trending.ai.data.local.LastDataCache
import whl.trending.ai.data.local.SettingsManager
import whl.trending.ai.data.model.ContributionCalendar
import whl.trending.ai.data.remote.GithubApi
import whl.trending.ai.data.remote.GithubEventDto
import whl.trending.ai.data.remote.GithubFollowing
import whl.trending.ai.data.remote.GithubUser

@OptIn(ExperimentalCoroutinesApi::class)
class GithubProfileViewModelTest {

    private class FakeAuthManager : AuthManager {
        val state = MutableStateFlow<AuthState>(AuthState.LoggedIn)
        override val isSupported: Boolean = true
        override val authState: StateFlow<AuthState> = state
        override fun signIn(source: String) {}
        override fun signOut() {}
        override suspend fun getAccessToken(): String? = "token"
    }

    private class FakeGithubApi(
        val onFetchUser: suspend () -> GithubUser = {
            GithubUser(login = "octo", name = "Octo Cat", followers = 10, following = 5, publicRepos = 3)
        },
        val onFetchContributions: suspend () -> ContributionCalendar = { profileCalendar(total = 7) },
    ) : GithubApi() {
        var requests = 0
        var eventRequests = 0

        override suspend fun fetchUser(githubToken: String): GithubUser {
            requests++
            return onFetchUser()
        }

        override suspend fun fetchContributionCalendar(githubToken: String, login: String): ContributionCalendar {
            requests++
            return onFetchContributions()
        }

        override suspend fun fetchReceivedEvents(
            githubToken: String,
            login: String,
            page: Int,
            perPage: Int,
        ): List<GithubEventDto> {
            requests++
            eventRequests++
            return emptyList()
        }
    }

    private class FakeTokenProvider(
        private val result: GithubTokenLookup = GithubTokenLookup.Available("gh-token"),
    ) : GithubTokenProvider() {
        override suspend fun lookup(): GithubTokenLookup = result
    }

    private class FakeFollowingProvider : FollowingProvider() {
        override suspend fun get(): FollowingInfo? = null
    }

    private class FakeOwnRepoEventsProvider : OwnRepoEventsProvider() {
        override suspend fun get(): List<GithubEventDto>? = null
    }

    private fun settings(login: String? = "octo", highlightsOnly: Boolean = true): SettingsManager =
        SettingsManager(MapSettings() as ObservableSettings).also {
            it.setFeedHighlightsOnly(highlightsOnly)
            it.setGithubIdentity(login, if (login == null) null else 1L)
        }

    private fun TestScope.cache() = LastDataCache(FakeCacheFileStore(), StandardTestDispatcher(testScheduler))

    private fun viewModel(
        cache: LastDataCache,
        auth: FakeAuthManager = FakeAuthManager(),
        githubApi: FakeGithubApi = FakeGithubApi(),
        settingsManager: SettingsManager = settings(),
        tokenProvider: GithubTokenProvider = FakeTokenProvider(),
        followingProvider: FollowingProvider = FakeFollowingProvider(),
    ) = GithubProfileViewModel(
        githubApi = githubApi,
        tokenProvider = tokenProvider,
        followingProvider = followingProvider,
        ownRepoEventsProvider = FakeOwnRepoEventsProvider(),
        authManager = { auth },
        settingsManager = settingsManager,
        cache = cache,
    )

    private fun cachedSnapshot(login: String = "octo", highlightsOnly: Boolean = true) = GithubProfileCache(
        login = login,
        githubUser = GithubUser(login = login, followers = 3),
        contributions = profileCalendar(total = 99),
        feedItems = listOf(profileFeedItem("cached-event")),
        highlightsOnly = highlightsOnly,
    )

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** 数据只在进子页时拉：VM 建出来（Activity 级常驻）不等于有人在看 */
    @Test
    fun nothingIsFetchedUntilLoad() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val api = FakeGithubApi()
        viewModel(cache(), githubApi = api)
        advanceUntilIdle()

        assertEquals(0, api.requests)
    }

    @Test
    fun loadFillsHeaderCountsAndContributions() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModel(cache())
        vm.load()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("octo", state.login)
        assertEquals("Octo Cat", state.githubUser?.name)
        assertEquals(10, state.githubUser?.followers)
        assertEquals(7, state.contributions?.total)
        assertTrue(state.feedEndReached)
    }

    @Test
    fun serverSaysNoTokenFlagsRelink() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModel(cache(), tokenProvider = FakeTokenProvider(GithubTokenLookup.Missing))
        vm.load()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.githubTokenMissing)
        assertTrue(state.feedUnavailable)
        assertNull(state.githubUser)
    }

    @Test
    fun tokenLookupFailureDoesNotFlagRelinkAndRetriesOnNextEntry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val api = FakeGithubApi()
        var result: GithubTokenLookup = GithubTokenLookup.Failed
        val tokenProvider = object : GithubTokenProvider() {
            override suspend fun lookup(): GithubTokenLookup = result
        }
        val vm = viewModel(cache(), githubApi = api, tokenProvider = tokenProvider)
        vm.load()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.githubTokenMissing)
        assertTrue(vm.uiState.value.feedUnavailable)

        result = GithubTokenLookup.Available("gh-token")
        vm.load()
        advanceUntilIdle()
        assertEquals(10, vm.uiState.value.githubUser?.followers)
    }

    @Test
    fun noLocalLoginMarksFeedUnavailableWithoutRequests() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val api = FakeGithubApi()
        val vm = viewModel(cache(), githubApi = api, settingsManager = settings(login = null))
        vm.load()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.feedUnavailable)
        assertEquals(0, api.requests)
    }

    @Test
    fun cacheHitFillsWholePageAndAutoRefreshes() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val cache = cache()
        cache.put(GithubProfileCache.KEY, cachedSnapshot())
        val gate = CompletableDeferred<GithubUser>()

        val vm = viewModel(cache, githubApi = FakeGithubApi(onFetchUser = { gate.await() }))
        vm.load()
        advanceUntilIdle()

        // 缓存整页秒出：计数/热力图/feed 都有，且顶部自动刷新中
        val mid = vm.uiState.value
        assertTrue(mid.isRefreshing)
        assertEquals(3, mid.githubUser?.followers)
        assertEquals(99, mid.contributions?.total)
        assertEquals(listOf("cached-event"), mid.feedItems.map { it.id })

        gate.complete(GithubUser(login = "octo", followers = 10))
        advanceUntilIdle()

        val end = vm.uiState.value
        assertFalse(end.isRefreshing)
        assertEquals(7, end.contributions?.total)
        assertEquals(10, end.githubUser?.followers)
    }

    @Test
    fun cacheOfAnotherLoginIsIgnored() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val cache = cache()
        cache.put(GithubProfileCache.KEY, cachedSnapshot(login = "someone-else"))
        val gate = CompletableDeferred<GithubUser>()

        val vm = viewModel(cache, githubApi = FakeGithubApi(onFetchUser = { gate.await() }))
        vm.load()
        advanceUntilIdle()

        val mid = vm.uiState.value
        assertFalse(mid.isRefreshing)
        assertNull(mid.githubUser)
        assertNull(mid.contributions)
        assertEquals(emptyList(), mid.feedItems)
        gate.complete(GithubUser(login = "octo"))
    }

    @Test
    fun loadWithoutCachePersistsSnapshot() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val cache = cache()

        val vm = viewModel(cache)
        vm.load()
        advanceUntilIdle()

        val persisted = cache.get<GithubProfileCache>(GithubProfileCache.KEY)
        assertNotNull(persisted)
        assertEquals("octo", persisted.login)
        assertEquals(7, persisted.contributions?.total)
        assertEquals(10, persisted.githubUser?.followers)
    }

    @Test
    fun cacheFilterMismatchUsesHeaderOnly() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val cache = cache()
        cache.put(GithubProfileCache.KEY, cachedSnapshot(highlightsOnly = true))
        val gate = CompletableDeferred<GithubUser>()

        val vm = viewModel(
            cache,
            githubApi = FakeGithubApi(onFetchUser = { gate.await() }),
            settingsManager = settings(highlightsOnly = false),
        )
        vm.load()
        advanceUntilIdle()

        // 档位不一致：计数/热力图可用，feed 不复用
        val mid = vm.uiState.value
        assertEquals(99, mid.contributions?.total)
        assertEquals(emptyList(), mid.feedItems)
        assertFalse(mid.highlightsOnly)
        gate.complete(GithubUser(login = "octo"))
    }

    @Test
    fun returningFromDrillDownDoesNotRefetch() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val api = FakeGithubApi()
        val vm = viewModel(cache(), githubApi = api)
        vm.load()
        advanceUntilIdle()
        val afterFirst = api.requests

        vm.load()
        advanceUntilIdle()

        assertEquals(afterFirst, api.requests)
    }

    /** 下拉刷新要真的重拉关注列表，不能被 provider 的会话缓存短路 */
    @Test
    fun refreshReloadsFollowingList() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var followingCalls = 0
        val api = object : GithubApi() {
            override suspend fun fetchFollowing(githubToken: String, page: Int, perPage: Int): List<GithubFollowing> {
                followingCalls++
                return emptyList()
            }
        }
        val tokenProvider = FakeTokenProvider()
        val vm = viewModel(cache(), tokenProvider = tokenProvider, followingProvider = FollowingProvider(api, tokenProvider))
        vm.load()
        advanceUntilIdle()
        assertEquals(1, followingCalls)

        vm.refresh()
        advanceUntilIdle()

        assertEquals(2, followingCalls)
        assertFalse(vm.uiState.value.isRefreshing)
    }

    @Test
    fun authTransitionResetsAndBumpsReloadKey() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val auth = FakeAuthManager()
        val vm = viewModel(cache(), auth = auth)
        vm.load()
        advanceUntilIdle()
        val key = vm.reloadKey.value
        assertNotNull(vm.uiState.value.githubUser)

        auth.state.value = AuthState.LoggedOut
        advanceUntilIdle()

        assertEquals(key + 1, vm.reloadKey.value)
        assertNull(vm.uiState.value.login)
        assertNull(vm.uiState.value.githubUser)
    }

    /** 页面触底可能在关注列表 / 自有仓库事件就绪前调 loadMoreFeed，精选档会按缺失的数据过滤 */
    @Test
    fun loadMoreBeforePreparationIsIgnored() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val api = FakeGithubApi()
        val tokenGate = CompletableDeferred<GithubTokenLookup>()
        val tokenProvider = object : GithubTokenProvider() {
            override suspend fun lookup(): GithubTokenLookup = tokenGate.await()
        }
        val vm = viewModel(cache(), githubApi = api, tokenProvider = tokenProvider)
        vm.load()
        advanceUntilIdle()

        vm.loadMoreFeed()
        advanceUntilIdle()
        assertEquals(0, api.eventRequests)
        assertFalse(vm.uiState.value.isFeedLoading)

        tokenGate.complete(GithubTokenLookup.Available("gh-token"))
        advanceUntilIdle()
        assertEquals(1, api.eventRequests)
    }

    /** 作废后迟到的贡献图不得写进下一个账号的状态与缓存 */
    @Test
    fun invalidateCancelsInFlightContributions() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val cache = cache()
        val auth = FakeAuthManager()
        val gate = CompletableDeferred<ContributionCalendar>()
        val vm = viewModel(cache, auth = auth, githubApi = FakeGithubApi(onFetchContributions = { gate.await() }))
        vm.load()
        advanceUntilIdle()

        auth.state.value = AuthState.LoggedOut
        advanceUntilIdle()
        gate.complete(profileCalendar(total = 42))
        advanceUntilIdle()

        assertNull(vm.uiState.value.contributions)
        assertNull(cache.get<GithubProfileCache>(GithubProfileCache.KEY)?.contributions)
    }

    @Test
    fun switchingFilterWithoutLoginKeepsFeedUnavailable() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModel(cache(), settingsManager = settings(login = null))
        vm.load()
        advanceUntilIdle()

        vm.setFeedFilter(false)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.feedUnavailable)
        assertFalse(vm.uiState.value.isFeedLoadingVisible)
    }

    @Test
    fun switchingFilterWithMissingTokenDoesNotSpin() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModel(cache(), tokenProvider = FakeTokenProvider(GithubTokenLookup.Missing))
        vm.load()
        advanceUntilIdle()

        vm.setFeedFilter(false)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.feedUnavailable)
        assertFalse(vm.uiState.value.isFeedLoadingVisible)
    }
}
