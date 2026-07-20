package whl.trending.ai.data.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class PickItemTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun producthunt_pick_opens_ph_post() {
        val payload = """
            {"source":"producthunt","title":"ZooData","url":"https://zoodata.ai/",
            "phUrl":"https://www.producthunt.com/products/zoodata?utm_source=api"}
        """.trimIndent()
        val item = json.decodeFromString<PickItem>(payload)
        assertEquals("https://www.producthunt.com/products/zoodata?utm_source=api", item.openUrl)
    }

    @Test
    fun producthunt_pick_without_ph_url_falls_back_to_url() {
        val payload = """
            {"source":"producthunt","title":"Legacy product","url":"https://example.com/"}
        """.trimIndent()
        val item = json.decodeFromString<PickItem>(payload)
        assertEquals("https://example.com/", item.openUrl)
    }

    @Test
    fun producthunt_pick_with_blank_ph_url_falls_back_to_url() {
        val payload = """
            {"source":"producthunt","title":"Product","url":"https://example.com/","phUrl":""}
        """.trimIndent()
        val item = json.decodeFromString<PickItem>(payload)
        assertEquals("https://example.com/", item.openUrl)
    }

    @Test
    fun non_producthunt_pick_always_opens_url() {
        val payload = """
            {"source":"github","title":"foo/bar","url":"https://github.com/foo/bar",
            "phUrl":"https://www.producthunt.com/products/other"}
        """.trimIndent()
        val item = json.decodeFromString<PickItem>(payload)
        assertEquals("https://github.com/foo/bar", item.openUrl)
    }
}
