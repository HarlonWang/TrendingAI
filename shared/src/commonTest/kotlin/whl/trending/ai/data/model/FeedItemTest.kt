package whl.trending.ai.data.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class FeedItemTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun hackernews_item_opens_discussion_thread_from_extra() {
        val payload = """
            {"source":"hackernews","externalId":"42","title":"Some story",
            "url":"https://www.beyondallreason.info/",
            "extra":{"hn_url":"https://news.ycombinator.com/item?id=42","time":1700000000}}
        """.trimIndent()
        val item = json.decodeFromString<FeedItem>(payload)
        assertEquals("https://news.ycombinator.com/item?id=42", item.openUrl)
    }

    @Test
    fun hackernews_item_without_hn_url_falls_back_to_url() {
        val payload = """
            {"source":"hackernews","externalId":"43","title":"Ask HN",
            "url":"https://news.ycombinator.com/item?id=43"}
        """.trimIndent()
        val item = json.decodeFromString<FeedItem>(payload)
        assertEquals("https://news.ycombinator.com/item?id=43", item.openUrl)
    }

    @Test
    fun hackernews_item_with_blank_hn_url_falls_back_to_url() {
        val payload = """
            {"source":"hackernews","externalId":"44","title":"Story",
            "url":"https://example.com/article","extra":{"hn_url":""}}
        """.trimIndent()
        val item = json.decodeFromString<FeedItem>(payload)
        assertEquals("https://example.com/article", item.openUrl)
    }

    @Test
    fun non_hackernews_item_always_opens_url() {
        val payload = """
            {"source":"github","externalId":"r1","title":"repo",
            "url":"https://github.com/foo/bar",
            "extra":{"hn_url":"https://news.ycombinator.com/item?id=1"}}
        """.trimIndent()
        val item = json.decodeFromString<FeedItem>(payload)
        assertEquals("https://github.com/foo/bar", item.openUrl)
    }

    @Test
    fun producthunt_item_opens_ph_post_from_extra() {
        val payload = """
            {"source":"producthunt","externalId":"p1","title":"ZooData",
            "url":"https://zoodata.ai/",
            "extra":{"ph_url":"https://www.producthunt.com/products/zoodata?utm_source=api"}}
        """.trimIndent()
        val item = json.decodeFromString<FeedItem>(payload)
        assertEquals("https://www.producthunt.com/products/zoodata?utm_source=api", item.openUrl)
    }

    @Test
    fun producthunt_item_without_ph_url_falls_back_to_url() {
        val payload = """
            {"source":"producthunt","externalId":"p2","title":"Legacy product",
            "url":"https://example.com/"}
        """.trimIndent()
        val item = json.decodeFromString<FeedItem>(payload)
        assertEquals("https://example.com/", item.openUrl)
    }

    @Test
    fun producthunt_item_with_blank_ph_url_falls_back_to_url() {
        val payload = """
            {"source":"producthunt","externalId":"p3","title":"Product",
            "url":"https://example.com/","extra":{"ph_url":""}}
        """.trimIndent()
        val item = json.decodeFromString<FeedItem>(payload)
        assertEquals("https://example.com/", item.openUrl)
    }
}
