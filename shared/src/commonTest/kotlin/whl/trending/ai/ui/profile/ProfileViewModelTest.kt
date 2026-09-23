package whl.trending.ai.ui.profile

import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.ObservableSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.concurrent.Volatile
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import whl.trending.ai.auth.AuthManager
import whl.trending.ai.auth.AuthState
import whl.trending.ai.data.local.FakeCacheFileStore
import whl.trending.ai.data.local.LastDataCache
import whl.trending.ai.data.local.AppLanguage
import whl.trending.ai.data.local.SettingsManager
import whl.trending.ai.data.model.LocalizedText
import whl.trending.ai.data.model.QuotaHelpRemoteConfig
import whl.trending.ai.data.model.MeUser
import whl.trending.ai.data.model.QuotaResponse
import whl.trending.ai.data.repository.UserRepository

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private class FakeAuthManager : AuthManager {
        val state = MutableStateFlow<AuthState>(AuthState.LoggedIn)
        @Volatile var token: String? = "token"
        override val isSupported: Boolean = true
        override val authState: StateFlow<AuthState> = state
        override fun signIn(source: String) {}
        override fun signOut() {}
        override suspend fun getAccessToken(): String? = token
    }

    private class FakeUserRepository(
        val onFetchMe: suspend () -> MeUser = { profileMeUser() },
        val onFetchQuota: suspend () -> QuotaResponse = {
            QuotaResponse(balance = 8, dailyGrant = 10, resetAt = "2026-07-24T00:00:00.000Z", tier = "user")
        },
    ) : UserRepository() {
        override suspend fun fetchMe(): MeUser = onFetchMe()
        override suspend fun fetchQuota(): QuotaResponse = onFetchQuota()
    }

    private fun settings(): SettingsManager = SettingsManager(MapSettings() as ObservableSettings)

    private fun TestScope.cache(store: FakeCacheFileStore = FakeCacheFileStore()) =
        LastDataCache(store, StandardTestDispatcher(testScheduler))

    private fun viewModel(
        cache: LastDataCache,
        auth: FakeAuthManager = FakeAuthManager(),
        repository: UserRepository = FakeUserRepository(),
        settingsManager: SettingsManager = settings(),
    ) = ProfileViewModel(
        repository = repository,
        authManager = { auth },
        settingsManager = settingsManager,
        cache = cache,
    )

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun cacheHitShowsIdentityAndAutoRefreshes() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val cache = cache()
        cache.put(ACCOUNT_CACHE_KEY, profileMeUser().copy(displayName = "Cached"))
        val gate = CompletableDeferred<MeUser>()

        val vm = viewModel(cache, repository = FakeUserRepository(onFetchMe = { gate.await() }))
        vm.load()
        advanceUntilIdle()

        val mid = vm.uiState.value
        assertFalse(mid.isLoading)
        assertTrue(mid.isRefreshing)
        assertEquals("Cached", mid.user?.displayName)

        gate.complete(profileMeUser())
        advanceUntilIdle()

        val end = vm.uiState.value
        assertFalse(end.isRefreshing)
        assertEquals("Octo", end.user?.displayName)
        assertEquals("Octo", cache.get<MeUser>(ACCOUNT_CACHE_KEY)?.displayName)
    }

    /** 没关联 GitHub 的邮箱用户也要能秒开：缓存不依赖 githubLogin */
    @Test
    fun emailOnlyUserIsCached() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val cache = cache()

        val vm = viewModel(cache, repository = FakeUserRepository(onFetchMe = { profileMeUser(login = null) }))
        vm.load()
        advanceUntilIdle()

        assertNotNull(cache.get<MeUser>(ACCOUNT_CACHE_KEY))
    }

    // 回归：登出重登后首次进页（无缓存路径），quota 先于 fetchMe 到达时，
    // 整页状态重建不得把已写入的 quota 抹掉（否则配额卡静默消失，返回再进才出现）
    @Test
    fun quotaSurvivesFullPageLoadRace() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val cache = cache() // 无缓存：登出已清
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

    /**
     * Hub 对匿名用户可达，所以「不是 LoggedIn 就摆登录引导」这个判断必须等会话恢复完
     * ——冷启动直奔账户页的登录用户，否则会先被当成未登录。
     */
    @Test
    fun loadWaitsForAuthStateToResolve() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val cache = cache()
        val auth = FakeAuthManager().apply { state.value = AuthState.Unknown }

        val vm = viewModel(cache, auth = auth)
        vm.load()
        advanceTimeBy(1_000) // 不能用 advanceUntilIdle：它会跨过落定超时，直接把页面判成未登录

        // 还没落定：不下匿名结论，停在加载态
        assertTrue(vm.uiState.value.isLoading)
        assertFalse(vm.uiState.value.loggedIn)
        assertNull(vm.uiState.value.user)

        auth.state.value = AuthState.LoggedIn
        advanceUntilIdle()

        assertTrue(vm.uiState.value.loggedIn)
        assertEquals("octo", vm.uiState.value.user?.githubLogin)
    }

    /**
     * 等落定是个无界等待，落不了定就永远转圈、没有任何提示。超时兜底把这类静默硬故障
     * 降级成「按未登录处理」，用户点一下就能重试。
     */
    @Test
    fun loadFallsBackToAnonymousWhenAuthStateNeverResolves() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val cache = cache()
        val auth = FakeAuthManager().apply { state.value = AuthState.Unknown }

        val vm = viewModel(cache, auth = auth)
        vm.load()
        advanceUntilIdle() // 虚拟时间跨过超时；authState 始终停在 Unknown

        assertFalse(vm.uiState.value.isLoading)
        assertFalse(vm.uiState.value.loggedIn)
        assertNull(vm.uiState.value.user)
    }

    @Test
    fun logoutClearsAccountAndGithubCaches() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val cache = cache()
        cache.put(ACCOUNT_CACHE_KEY, profileMeUser())
        cache.put(GithubProfileCache.KEY, GithubProfileCache(login = "octo"))
        val auth = FakeAuthManager()

        viewModel(cache, auth = auth)
        advanceUntilIdle()

        auth.state.value = AuthState.LoggedOut
        advanceUntilIdle()

        assertNull(cache.get<MeUser>(ACCOUNT_CACHE_KEY))
        assertNull(cache.get<GithubProfileCache>(GithubProfileCache.KEY))
    }

    @Test
    fun quotaHelpFollowsCacheWrittenAfterCreationAndLanguageSwitch() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val settings = settings().also { it.setLanguage(AppLanguage.ENGLISH) }
        val vm = viewModel(cache(), settingsManager = settings)
        val collector = launch { vm.quotaHelp.collect {} }
        advanceUntilIdle()
        assertNull(vm.quotaHelp.value)

        settings.setQuotaHelp(
            QuotaHelpRemoteConfig(
                title = LocalizedText("说明", "About"),
                paragraphs = listOf(LocalizedText("一", "One")),
            )
        )
        advanceUntilIdle()
        assertEquals(QuotaHelpContent("About", listOf("One")), vm.quotaHelp.value)

        settings.setLanguage(AppLanguage.CHINESE)
        advanceUntilIdle()
        assertEquals(QuotaHelpContent("说明", listOf("一")), vm.quotaHelp.value)
        collector.cancel()
    }
}
