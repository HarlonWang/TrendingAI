package whl.trending.chat.model

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

/** 可编程返回序列的假拉取器：按调用次序弹出预设响应，并计数。 */
private class FakeFetch(private val responses: MutableList<ChatModelsResponse>) {
    var fetchCount = 0

    suspend fun fetch(): ChatModelsResponse {
        fetchCount++
        return responses.removeAt(0)
    }
}

/** [ChatModelsProvider] 缓存准入判定单测：完整响应才长期缓存，破损响应当次可用、下次重试。 */
class ChatModelsProviderTest {

    private val free = ChatModelOption(id = "gpt-5.6-luna", minTier = ChatModelOption.TIER_USER)
    private val pro = ChatModelOption(id = "gpt-6", minTier = ChatModelOption.TIER_PRO)
    private val complete = ChatModelsResponse(models = listOf(free, pro), default = free.id)

    @BeforeTest
    fun setUp() = ChatModelsProvider.resetForTests()

    @AfterTest
    fun tearDown() = ChatModelsProvider.resetForTests()

    private fun inject(vararg responses: ChatModelsResponse): FakeFetch =
        FakeFetch(responses.toMutableList()).also { ChatModelsProvider.fetch = it::fetch }

    @Test
    fun complete_response_is_cached_and_not_refetched() = runTest {
        val fake = inject(complete)
        assertEquals(complete, ChatModelsProvider.get())
        assertEquals(complete, ChatModelsProvider.get()) // 第二次命中缓存
        assertEquals(1, fake.fetchCount)
        assertEquals(complete, ChatModelsProvider.cachedOrEmpty())
    }

    /** 核心准入断言：default 缺失的响应当次照常返回，但不进缓存——下次重试可自愈，
     *  不会被一次坏响应钉住整个进程 */
    @Test
    fun response_missing_default_is_served_once_but_not_cached() = runTest {
        val broken = ChatModelsResponse(models = listOf(free, pro), default = "")
        val fake = inject(broken, complete)
        assertEquals(broken, ChatModelsProvider.get())          // 当次可用
        assertEquals(ChatModelsResponse(), ChatModelsProvider.cachedOrEmpty()) // 未缓存
        assertEquals(complete, ChatModelsProvider.get())        // 重试拿到修复后的响应
        assertEquals(2, fake.fetchCount)
        assertEquals(complete, ChatModelsProvider.cachedOrEmpty()) // 这次才缓存
    }

    @Test
    fun response_with_dangling_default_is_not_cached() = runTest {
        val dangling = ChatModelsResponse(models = listOf(free, pro), default = "ghost-model")
        inject(dangling, complete)
        ChatModelsProvider.get()
        assertEquals(ChatModelsResponse(), ChatModelsProvider.cachedOrEmpty())
        assertEquals(complete, ChatModelsProvider.get())
    }

    /** 既有语义回归：空目录（拉取失败降级）照旧不缓存、下次重试 */
    @Test
    fun empty_catalog_is_not_cached() = runTest {
        val fake = inject(ChatModelsResponse(), complete)
        assertEquals(ChatModelsResponse(), ChatModelsProvider.get())
        assertEquals(complete, ChatModelsProvider.get())
        assertEquals(2, fake.fetchCount)
    }
}
