package whl.trending.ai.data.local

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * 缓存 schema 版本：数据模型发生不兼容改动时 bump，老文件读取解码失败会被自动删除，
 * 无需迁移逻辑。
 */
private const val CACHE_SCHEMA_VERSION = 1

/**
 * 「上次数据缓存」统一入口（SWR）：进入页面先展示上次成功获取的数据，再后台刷新。
 *
 * 语义约定（详见 docs/superpowers/specs/2026-07-03-last-data-cache-design.md）：
 * - 只有网络成功才 [put]，写入是对该 key 的全量覆盖，不做增量合并；
 * - [get] 在文件缺失或解码失败时返回 null（损坏文件顺手删除），调用方回退骨架屏；
 * - 读写都切到后台调度器，调用方可安全地在主线程协程里使用。
 */
class LastDataCache(
    @PublishedApi internal val store: CacheFileStore = platformCacheFileStore(),
    /** 文件 IO 调度器；测试注入 TestDispatcher 以获得确定性 */
    @PublishedApi internal val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    @PublishedApi
    internal val json = Json { ignoreUnknownKeys = true }

    @PublishedApi
    internal fun fileName(key: String): String = "v${CACHE_SCHEMA_VERSION}_$key.json"

    suspend inline fun <reified T> get(key: String): T? = withContext(dispatcher) {
        val name = fileName(key)
        val content = store.read(name) ?: return@withContext null
        runCatching { json.decodeFromString<T>(content) }.getOrElse {
            store.delete(name)
            null
        }
    }

    suspend inline fun <reified T> put(key: String, value: T) {
        withContext(dispatcher) {
            // 序列化/IO 失败只降级为「本次不缓存」，不影响调用方；打日志保留可观测性
            runCatching { store.write(fileName(key), json.encodeToString(value)) }
                .onFailure { println("LastDataCache: put($key) failed: $it") }
        }
    }

    suspend fun remove(key: String) = withContext(dispatcher) {
        store.delete(fileName(key))
    }
}

val globalLastDataCache by lazy { LastDataCache() }
