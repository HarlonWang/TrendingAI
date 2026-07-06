package whl.trending.ai.data.repository

import kotlin.concurrent.Volatile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import whl.trending.ai.data.model.ChatModelOption
import whl.trending.ai.data.remote.TrendingApi

/**
 * 聊天模型目录的进程级缓存 + 预热。
 *
 * 根因：模型选择器 chip 仅在 `models.size > 1` 时显示，而模型目录原先在每个 [ChatViewModel] 的 init 里
 * 各自网络拉取。首个打开的会话触发后端冷路径（`/api/chat/models` 冷 isolate 同步拉 OpenAI /models，耗时数秒），
 * 期间 chip 一直不出现——被误认为「没有模型列表」；后续会话命中后端热缓存才秒出。
 *
 * 修法：全进程只拉一次并缓存（[get]），且在应用启动时[warmUp]预热，让 chip 在任何会话打开前就绪。
 * 失败不缓存空结果，下次自动重试。
 */
object ChatModelsProvider {
    private val api = TrendingApi()
    private val mutex = Mutex()

    @Volatile
    private var cache: List<ChatModelOption>? = null

    /** 取模型目录：命中缓存直接返回；否则拉一次，成功才缓存（失败返回空、下次重试）。 */
    suspend fun get(): List<ChatModelOption> {
        cache?.let { return it }
        return mutex.withLock {
            cache?.let { return it }
            val fetched = runCatching { api.fetchChatModels() }.getOrDefault(emptyList())
            if (fetched.isNotEmpty()) cache = fetched
            fetched
        }
    }

    /** 应用启动时预热（fire-and-forget），避免首个 chat 等冷拉取。 */
    fun warmUp(scope: CoroutineScope) {
        scope.launch { runCatching { get() } }
    }

    /** 仅测试用：重置缓存。 */
    fun resetForTest() {
        cache = null
    }
}
