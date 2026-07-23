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
import whl.trending.ai.auth.FollowingProvider
import whl.trending.ai.auth.GithubTokenProvider
import whl.trending.ai.auth.OwnRepoEventsProvider
import whl.trending.ai.data.local.FakeCacheFileStore
import whl.trending.ai.data.local.LastDataCache
import whl.trending.ai.data.local.SettingsManager
import whl.trending.ai.data.model.ContributionCalendar
import whl.trending.ai.data.model.MeUser
import whl.trending.ai.data.model.QuotaResponse
import whl.trending.ai.data.remote.GithubApi
import whl.trending.ai.data.remote.GithubEventDto
import whl.trending.ai.data.remote.GithubUser
import whl.trending.ai.data.repository.UserRepository

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private class FakeAuthManager : AuthManager {
        val state = MutableStateFlow<AuthState>(AuthState.LoggedIn)
        @Volatile var token: String? = "token"
        override val isSupported: Boolean = true
        override val authState: StateFlow<AuthState> = state
        override fun signIn(source: String) {}
        override fun signIn(source: String, method: whl.trending.ai.auth.SignInMethod) {}
        override fun signOut() {}
        override suspend fun getAccessToken(): String? = token
    }

    private class FakeUserRepository(
        val onFetchMe: suspend () -> MeUser = { profileMeUser() },
        val onFetchQuota: suspend () -> QuotaResponse = {
            QuotaResponse(balance = 8, dailyGrant = 10, resetAt = "2026-07-24T00:00:00.000Z", tier = "user")
        },
    ) : UserRepository() {
        override suspend fun fetchMe(accessToken: String): MeUser = onFetchMe()
        override suspend fun fetchQuota(accessToken: String?): QuotaResponse = onFetchQuota()
    }

    private class FakeGithubApi : GithubApi() {
        override suspend fun fetchUser(githubToken: String): GithubUser =
            GithubUser(login = "octo", followers = 10, following = 5, publicRepos = 3)

        override suspend fun fetchContributionCalendar(githubToken: String, login: String): ContributionCalendar =
            profileCalendar(total = 7)

        override suspend fun fetchReceivedEvents(
            githubToken: String,
            login: String,
            page: Int,
            perPage: Int,
        ): List<GithubEventDto> = emptyList()
    }

    private class FakeTokenProvider : GithubTokenProvider() {
        override suspend fun get(): String? = "gh-token"
    }

    private class FakeFollowingProvider : FollowingProvider() {
        override suspend fun get() = null
    }

    private class FakeOwnRepoEventsProvider : OwnRepoEventsProvider() {
        override suspend fun get(): List<GithubEventDto>? = null
    }

    private fun settings(highlightsOnly: Boolean = true): SettingsManager =
        SettingsManager(MapSettings() as ObservableSettings).also { it.setFeedHighlightsOnly(highlightsOnly) }

    private fun TestScope.cache(store: FakeCacheFileStore = FakeCacheFileStore()) =
        LastDataCache(store, StandardTestDispatcher(testScheduler))

    private fun viewModel(
        cache: LastDataCache,
        auth: FakeAuthManager = FakeAuthManager(),
        repository: UserRepository = FakeUserRepository(),
        settingsManager: SettingsManager = settings(),
    ) = ProfileViewModel(
        repository = repository,
        githubApi = FakeGithubApi(),
        tokenProvider = FakeTokenProvider(),
        followingProvider = FakeFollowingProvider(),
        ownRepoEventsProvider = FakeOwnRepoEventsProvider(),
        authManager = { auth },
        settingsManager = settingsManager,
        cache = cache,
    )

    private fun cachedSnapshot(highlightsOnly: Boolean = true) = ProfileCache(
        login = "octo",
        user = profileMeUser(),
        githubUser = GithubUser(login = "octo", followers = 3),
        contributions = profileCalendar(total = 99),
        feedItems = listOf(profileFeedItem("cached-event")),
        highlightsOnly = highlightsOnly,
    )

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun cacheHitFillsWholePageAndAutoRefreshes() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val cache = cache()
        cache.put(ProfileCache.KEY, cachedSnapshot())
        val gate = CompletableDeferred<MeUser>()

        val vm = viewModel(cache, repository = FakeUserRepository(onFetchMe = { gate.await() }))
        vm.load()
        advanceUntilIdle()

        // 缓存整页秒出：header/计数/热力图/feed 都有，且顶部自动刷新中
        val mid = vm.uiState.value
        assertFalse(mid.isLoading)
        assertTrue(mid.isRefreshing)
        assertEquals("octo", mid.user?.githubLogin)
        assertEquals(3, mid.githubUser?.followers)
        assertEquals(99, mid.contributions?.total)
        assertEquals(listOf("cached-event"), mid.feedItems.map { it.id })

        gate.complete(profileMeUser())
        advanceUntilIdle()

        // 刷新完成：热力图/计数替换为最新
        val end = vm.uiState.value
        assertFalse(end.isRefreshing)
        assertEquals(7, end.contributions?.total)
        assertEquals(10, end.githubUser?.followers)
    }

    @Test
    fun loadWithoutCachePersistsSnapshot() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val cache = cache()

        val vm = viewModel(cache)
        vm.load()
        advanceUntilIdle()

        val persisted = cache.get<ProfileCache>(ProfileCache.KEY)
        assertNotNull(persisted)
        assertEquals("octo", persisted.login)
        assertEquals(7, persisted.contributions?.total)
        assertEquals(10, persisted.githubUser?.followers)
    }

    @Test
    fun cacheFilterMismatchUsesHeaderOnly() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val cache = cache()
        cache.put(ProfileCache.KEY, cachedSnapshot(highlightsOnly = true))
        val gate = CompletableDeferred<MeUser>()

        val vm = viewModel(
            cache,
            repository = FakeUserRepository(onFetchMe = { gate.await() }),
            settingsManager = settings(highlightsOnly = false),
        )
        vm.load()
        advanceUntilIdle()

        // 档位不一致：header/热力图可用，feed 不复用
        val mid = vm.uiState.value
        assertEquals("octo", mid.user?.githubLogin)
        assertEquals(99, mid.contributions?.total)
        assertEquals(emptyList(), mid.feedItems)
        assertFalse(mid.highlightsOnly)
    }

    // 回归：登出重登后首次进页（无缓存路径），quota 先于 fetchMe 到达时，
    // 整页状态重建不得把已写入的 quota 抹掉（否则配额卡静默消失，返回再进才出现）
    @Test
    fun quotaSurvivesFullPageLoadRace() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val cache = cache() // 无缓存：登出已清 ProfileCache
        val gate = CompletableDeferred<MeUser>()

        val vm = viewModel(cache, repository = FakeUserRepository(onFetchMe = { gate.await() }))
        vm.load()
        advanceUntilIdle() // quota 已返回，fetchMe 仍挂起

        assertEquals(8, vm.uiState.value.quota?.balance)

        gate.complete(profileMeUser())
        advanceUntilIdle()

        val end = vm.uiState.value
        assertEquals("octo", end.user?.githubLogin)
        assertEquals(8, end.quota?.balance)
        assertFalse(end.quotaError)
    }

    // 回归：token 刷新瞬态为 null 时，loadQuota 不得请求匿名档覆盖登录/Pro 用户已有的真实余额
    @Test
    fun quotaNotOverwrittenWhenTokenNull() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val cache = cache()
        val auth = FakeAuthManager()

        val vm = viewModel(cache, auth = auth)
        vm.load()
        advanceUntilIdle()
        assertEquals(8, vm.uiState.value.quota?.balance) // 登录档余额

        // 模拟刷新瞬态：token 变 null。此时重拉余额不应退到匿名档（5/5）覆盖登录档
        auth.token = null
        vm.refresh()
        advanceUntilIdle()

        assertEquals(8, vm.uiState.value.quota?.balance) // 旧登录档保留，未被匿名覆盖
    }

    @Test
    fun logoutClearsProfileCache() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val cache = cache()
        cache.put(ProfileCache.KEY, cachedSnapshot())
        val auth = FakeAuthManager()

        viewModel(cache, auth = auth)
        advanceUntilIdle()

        auth.state.value = AuthState.LoggedOut
        advanceUntilIdle()

        assertNull(cache.get<ProfileCache>(ProfileCache.KEY))
    }
}
