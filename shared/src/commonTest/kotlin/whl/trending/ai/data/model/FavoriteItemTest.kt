package whl.trending.ai.data.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class FavoriteItemTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun target_url_prefers_open_url() {
        val item = FavoriteItem(
            url = "https://zoodata.ai/",
            title = "ZooData",
            source = "producthunt",
            openUrl = "https://www.producthunt.com/products/zoodata"
        )
        assertEquals("https://www.producthunt.com/products/zoodata", item.targetUrl)
    }

    @Test
    fun target_url_falls_back_to_url_when_open_url_absent() {
        val item = FavoriteItem(url = "https://github.com/foo/bar", title = "foo/bar", source = "github")
        assertEquals("https://github.com/foo/bar", item.targetUrl)
    }

    @Test
    fun target_url_falls_back_to_url_when_open_url_blank() {
        val item = FavoriteItem(
            url = "https://example.com/",
            title = "Product",
            source = "producthunt",
            openUrl = ""
        )
        assertEquals("https://example.com/", item.targetUrl)
    }

    /** 旧版本存下的收藏没有 openUrl 字段，反序列化后须回退到 url，不能崩 */
    @Test
    fun legacy_payload_without_open_url_decodes_and_falls_back() {
        val payload = """
            {"url":"https://example.com/","title":"Legacy","source":"producthunt","savedAt":1700000000}
        """.trimIndent()
        val item = json.decodeFromString<FavoriteItem>(payload)
        assertEquals("https://example.com/", item.targetUrl)
    }
}
