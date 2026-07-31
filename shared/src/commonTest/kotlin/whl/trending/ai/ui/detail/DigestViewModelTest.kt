package whl.trending.ai.ui.detail

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
import kotlin.test.assertTrue
import whl.trending.ai.data.local.FakeCacheFileStore
import whl.trending.ai.data.local.LastDataCache
import whl.trending.ai.data.local.SettingsManager
import whl.trending.ai.data.local.SummaryLanguage
import whl.trending.ai.data.remote.DetailSummaryApi
import whl.trending.ai.data.remote.DigestError
import whl.trending.ai.data.remote.DigestException
import whl.trending.ai.data.remote.DigestResult

@OptIn(ExperimentalCoroutinesApi::class)
class DigestViewModelTest {

    private class FakeApi(
        val onStream: suspend (lang: String) -> DigestResult,
    ) : DetailSummaryApi() {
        val langs = mutableListOf<String>()
        override suspend fun stream(
            source: String,
            externalId: String,
            lang: String,
            onDelta: (String) -> Unit,
        ): DigestResult {
            langs += lang
            return onStream(lang)
        }
    }

    private fun settings(lang: SummaryLanguage = SummaryLanguage.CHINESE): SettingsManager =
        SettingsManager(MapSettings() as ObservableSettings).also { it.setSummaryLanguage(lang) }

    private fun TestScope.cache(store: FakeCacheFileStore) =
        LastDataCache(store, StandardTestDispatcher(testScheduler))

    private fun viewModel(
        api: DetailSummaryApi,
        settings: SettingsManager,
        cache: LastDataCache,
    ) = DigestViewModel("hackernews", "49095865", api, settings, cache, track = { _, _ -> })

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun streamsThenCachesMarkdown() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = FakeCacheFileStore()
        val vm = viewModel(
            api = FakeApi { DigestResult("### 解读", cached = false) },
            settings = settings(),
            cache = cache(store),
        )
        advanceUntilIdle()

        assertEquals("### 解读", vm.uiState.value.markdown)
        assertEquals(false, vm.uiState.value.isStreaming)
        assertTrue(store.files.keys.any { it.contains("digest_v1_hackernews_49095865_zh") })
    }

    @Test
    fun cacheHitSkipsRequest() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val cache = cache(FakeCacheFileStore())
        cache.put("digest_v1_hackernews_49095865_zh", CachedDigest("缓存里的解读"))
        val api = FakeApi { error("解读已缓存，不应再请求") }

        val vm = viewModel(api, settings(), cache)
        advanceUntilIdle()

        assertEquals("缓存里的解读", vm.uiState.value.markdown)
        assertTrue(api.langs.isEmpty())
    }

    /**
     * 生成一篇解读要几十秒，期间用户完全可以去设置里改摘要语言。
     * 缓存键必须来自请求发出时那一次语言解析，否则中文正文会落到英文键上，
     * 下次以中文进入时读不到、以英文进入时反而读到中文。
     */
    @Test
    fun cacheKeyFollowsRequestLanguageEvenIfItChangesMidFlight() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = FakeCacheFileStore()
        val settings = settings(SummaryLanguage.CHINESE)
        val gate = CompletableDeferred<DigestResult>()
        val api = FakeApi { gate.await() }

        val vm = viewModel(api, settings, cache(store))
        advanceUntilIdle()
        assertEquals(listOf("zh"), api.langs)

        // 流式进行中，用户改了摘要语言
        settings.setSummaryLanguage(SummaryLanguage.ENGLISH)
        advanceUntilIdle()
        gate.complete(DigestResult("中文解读", cached = false))
        advanceUntilIdle()

        val keys = store.files.keys
        assertTrue(keys.any { it.contains("digest_v1_hackernews_49095865_zh") }, "应写回请求时的 zh 键：$keys")
        assertTrue(keys.none { it.contains("digest_v1_hackernews_49095865_en") }, "不应写到 en 键：$keys")
    }

    /**
     * 服务端零 delta 走完（内容过滤拒答等）照样发 done:true。不拦下来就会写入空缓存，
     * 之后每次进页面都命中空内容 → 永久空白且无重试入口，卸载重装才能恢复。
     */
    @Test
    fun emptyDigestIsTreatedAsFailureAndNotCached() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = FakeCacheFileStore()
        val vm = viewModel(
            api = FakeApi { DigestResult("", cached = false) },
            settings = settings(),
            cache = cache(store),
        )
        advanceUntilIdle()

        assertTrue(vm.uiState.value.error is DigestError.Retryable)
        assertEquals(false, vm.uiState.value.isStreaming)
        assertTrue(store.files.isEmpty(), "空正文不应落缓存：$store.files")
    }

    /** 历史版本可能已写进空缓存，命中它同样要当作未命中重新生成 */
    @Test
    fun blankCachedDigestIsIgnoredAndRegenerated() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val cache = cache(FakeCacheFileStore())
        cache.put("digest_v1_hackernews_49095865_zh", CachedDigest(""))
        val api = FakeApi { DigestResult("重新生成的解读", cached = false) }

        val vm = viewModel(api, settings(), cache)
        advanceUntilIdle()

        assertEquals("重新生成的解读", vm.uiState.value.markdown)
        assertEquals(listOf("zh"), api.langs)
    }

    @Test
    fun failureDropsPartialMarkdownAndKeepsErrorClassification() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = FakeCacheFileStore()
        val vm = viewModel(
            api = FakeApi { throw DigestException(DigestError.LoginRequired) },
            settings = settings(),
            cache = cache(store),
        )
        advanceUntilIdle()

        assertEquals(DigestError.LoginRequired, vm.uiState.value.error)
        assertEquals("", vm.uiState.value.markdown)
        assertTrue(store.files.isEmpty(), "失败不应落缓存")
    }
}
