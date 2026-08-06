package whl.trending.ai.data.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    @Test
    fun producthunt_gallery_keeps_order_and_appends_size_params() {
        val payload = """
            {"source":"producthunt","externalId":"p4","title":"Product",
            "url":"https://example.com/",
            "extra":{"gallery":["https://ph-files.imgix.net/a.png?auto=format",
            "https://ph-files.imgix.net/b.png"]}}
        """.trimIndent()
        val item = json.decodeFromString<FeedItem>(payload)
        assertEquals(
            listOf(
                "https://ph-files.imgix.net/a.png?auto=format&fm=webp&q=70&w=720&fit=max",
                "https://ph-files.imgix.net/b.png?fm=webp&q=70&w=720&fit=max"
            ),
            item.galleryImageUrls(720)
        )
    }

    @Test
    fun producthunt_gallery_drops_blank_entries_and_caps_count() {
        val urls = (1..7).joinToString(",") { "\"https://ph-files.imgix.net/$it.png\"" }
        val payload = """
            {"source":"producthunt","externalId":"p5","title":"Product",
            "url":"https://example.com/","extra":{"gallery":["",$urls]}}
        """.trimIndent()
        val item = json.decodeFromString<FeedItem>(payload)
        val gallery = item.galleryImageUrls(720)
        assertEquals(FeedItem.GALLERY_MAX, gallery.size)
        assertEquals("https://ph-files.imgix.net/1.png?fm=webp&q=70&w=720&fit=max", gallery.first())
    }

    @Test
    fun producthunt_item_without_gallery_has_no_images() {
        val payload = """
            {"source":"producthunt","externalId":"p6","title":"Legacy product",
            "url":"https://example.com/","extra":{"ph_url":"https://www.producthunt.com/x"}}
        """.trimIndent()
        val item = json.decodeFromString<FeedItem>(payload)
        assertTrue(item.galleryImageUrls(720).isEmpty())
    }

    @Test
    fun non_producthunt_item_has_no_gallery_images() {
        val payload = """
            {"source":"hackernews","externalId":"h9","title":"Story",
            "url":"https://example.com/","extra":{"gallery":["https://img.example/a.png"]}}
        """.trimIndent()
        val item = json.decodeFromString<FeedItem>(payload)
        assertTrue(item.galleryImageUrls(720).isEmpty())
    }
}
