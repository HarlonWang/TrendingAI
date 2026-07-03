package whl.trending.ai.data.local

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 内存版文件存取：单测替身，同时可断言底层文件状态 */
class FakeCacheFileStore : CacheFileStore {
    val files = mutableMapOf<String, String>()
    override fun read(name: String): String? = files[name]
    override fun write(name: String, content: String) {
        files[name] = content
    }
    override fun delete(name: String) {
        files.remove(name)
    }
}

@Serializable
private data class CachePayload(val id: Int, val title: String)

class LastDataCacheTest {

    @Test
    fun putThenGetReturnsSameValue() = runTest {
        val cache = LastDataCache(FakeCacheFileStore())
        cache.put("picks_zh", CachePayload(1, "hello"))
        assertEquals(CachePayload(1, "hello"), cache.get("picks_zh"))
    }

    @Test
    fun getReturnsNullWhenMissing() = runTest {
        val cache = LastDataCache(FakeCacheFileStore())
        assertNull(cache.get<CachePayload>("picks_zh"))
    }

    @Test
    fun putOverwritesPreviousValue() = runTest {
        val cache = LastDataCache(FakeCacheFileStore())
        cache.put("picks_zh", CachePayload(1, "old"))
        cache.put("picks_zh", CachePayload(2, "new"))
        assertEquals(CachePayload(2, "new"), cache.get("picks_zh"))
    }

    @Test
    fun corruptedContentReturnsNullAndDeletesFile() = runTest {
        val store = FakeCacheFileStore()
        val cache = LastDataCache(store)
        cache.put("picks_zh", CachePayload(1, "ok"))
        val fileName = store.files.keys.single()
        store.files[fileName] = "{ not valid json"
        assertNull(cache.get<CachePayload>("picks_zh"))
        assertTrue(store.files.isEmpty(), "损坏文件应被删除")
    }

    @Test
    fun decodeIgnoresUnknownKeys() = runTest {
        val store = FakeCacheFileStore()
        val cache = LastDataCache(store)
        cache.put("picks_zh", CachePayload(1, "ok"))
        val fileName = store.files.keys.single()
        store.files[fileName] = """{"id":1,"title":"ok","futureField":true}"""
        assertEquals(CachePayload(1, "ok"), cache.get("picks_zh"))
    }

    @Test
    fun removeDeletesEntry() = runTest {
        val store = FakeCacheFileStore()
        val cache = LastDataCache(store)
        cache.put("profile", CachePayload(1, "me"))
        cache.remove("profile")
        assertNull(cache.get<CachePayload>("profile"))
        assertTrue(store.files.isEmpty())
    }

    @Test
    fun keysAreIsolated() = runTest {
        val cache = LastDataCache(FakeCacheFileStore())
        cache.put("feed_hackernews_zh", CachePayload(1, "hn"))
        cache.put("feed_producthunt_zh", CachePayload(2, "ph"))
        assertEquals(CachePayload(1, "hn"), cache.get("feed_hackernews_zh"))
        assertEquals(CachePayload(2, "ph"), cache.get("feed_producthunt_zh"))
    }

    @Test
    fun fileNameCarriesSchemaVersionPrefix() = runTest {
        val store = FakeCacheFileStore()
        val cache = LastDataCache(store)
        cache.put("picks_zh", CachePayload(1, "ok"))
        val fileName = store.files.keys.single()
        assertTrue(fileName.startsWith("v1_"), "文件名应带 schema 版本前缀，实际：$fileName")
    }
}
