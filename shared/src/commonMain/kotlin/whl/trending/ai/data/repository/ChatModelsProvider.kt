package whl.trending.ai.data.repository

import kotlin.concurrent.Volatile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import whl.trending.ai.data.model.ChatModelsResponse
import whl.trending.ai.data.model.catalogDefaultChatModel
import whl.trending.ai.data.remote.TrendingApi

/**
 * 聊天模型目录的进程级缓存 + 预热：全进程只拉一次，启动时 [warmUp]——后端冷路径耗时数秒，
 * 各会话自拉会让模型选择器 chip 迟迟不出现。失败不缓存空结果，下次自动重试。
 */
object ChatModelsProvider {
    /** 测试注入口（FakeApi 覆写模式）；生产恒为真实 API。 */
    internal var api: TrendingApi = TrendingApi()
    private val mutex = Mutex()

    @Volatile
    private var cache: ChatModelsResponse? = null

    /**
     * 取模型目录：命中缓存直接返回；否则拉一次，**完整**（目录非空且 default 指向目录内）才缓存——
     * 否则一次坏响应会钉住整个进程、后端恢复后也不自愈。
     */
    suspend fun get(): ChatModelsResponse {
        cache?.let { return it }
        return mutex.withLock {
            cache?.let { return it }
            val fetched = runCatching { api.fetchChatModels() }.getOrDefault(ChatModelsResponse())
            if (fetched.models.isNotEmpty() && catalogDefaultChatModel(fetched) != null) cache = fetched
            fetched
        }
    }

    /** 测试用：清缓存并复位注入。 */
    internal fun resetForTests() {
        cache = null
        api = TrendingApi()
    }

    /** 已缓存的目录（无网络副作用）；尚未拉到时为空目录。发送热路径用它，避免冷拉阻塞聊天请求。 */
    fun cachedOrEmpty(): ChatModelsResponse = cache ?: ChatModelsResponse()

    /** 应用启动时预热（fire-and-forget），避免首个 chat 等冷拉取。 */
    fun warmUp(scope: CoroutineScope) {
        scope.launch { runCatching { get() } }
    }
}
