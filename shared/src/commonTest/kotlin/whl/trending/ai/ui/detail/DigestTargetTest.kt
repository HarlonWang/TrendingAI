package whl.trending.ai.ui.detail

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import whl.trending.ai.data.model.FavoriteItem
import whl.trending.ai.data.model.FeedExtra
import whl.trending.ai.data.model.FeedItem
import whl.trending.ai.data.model.PickItem

/**
 * 「哪些条目进解读页、哪些仍外开」的判定。这条边界一错，要么 GitHub 条目丢了 README 页，
 * 要么存量收藏点开就是 404。
 */
class DigestTargetTest {

    @Test
    fun `HN 条目进解读页，链接取 HN 讨论页`() {
        val item = FeedItem(
            source = "hackernews",
            externalId = "49095865",
            title = "KOReader",
            url = "https://koreader.rocks/",
            summary = "电子书阅读器。",
            extra = FeedExtra(hnUrl = "https://news.ycombinator.com/item?id=49095865"),
        )
        val target = item.digestTarget()
        assertEquals("hackernews", target?.source)
        assertEquals("49095865", target?.externalId)
        assertEquals("https://news.ycombinator.com/item?id=49095865", target?.url)
        assertEquals("电子书阅读器。", target?.summary)
    }

    @Test
    fun `PH 条目进解读页`() {
        val item = FeedItem(
            source = "producthunt",
            externalId = "1200627",
            title = "Prelint",
            url = "https://www.producthunt.com/r/ABC",
            extra = FeedExtra(phUrl = "https://www.producthunt.com/products/prelint"),
        )
        assertEquals("https://www.producthunt.com/products/prelint", item.digestTarget()?.url)
    }

    @Test
    fun `GitHub 条目不进解读页——它有 README 详情页`() {
        val item = FeedItem(source = "github", externalId = "octo/demo", url = "https://github.com/octo/demo")
        assertNull(item.digestTarget())
    }

    @Test
    fun `未知来源不进解读页`() {
        val item = FeedItem(source = "reddit", externalId = "abc", url = "https://reddit.com/r/x")
        assertNull(item.digestTarget())
    }

    @Test
    fun `缺 externalId 不进解读页——服务端按目录查条目，合成键必然 404`() {
        val item = FeedItem(source = "hackernews", externalId = "", url = "https://example.com/")
        assertNull(item.digestTarget())
    }

    @Test
    fun `精选条目复用同一判定`() {
        val pick = PickItem(
            source = "producthunt",
            externalId = "1200627",
            title = "Prelint",
            url = "https://www.producthunt.com/r/ABC",
            phUrl = "https://www.producthunt.com/products/prelint",
        )
        assertEquals("1200627", pick.digestTarget()?.externalId)
        assertNull(PickItem(source = "github", externalId = "octo/demo").digestTarget())
    }

    @Test
    fun `存量收藏没有 externalId 时回落外开`() {
        val legacy = FavoriteItem(
            url = "https://news.ycombinator.com/item?id=1",
            title = "旧收藏",
            source = "hackernews",
        )
        assertNull(legacy.digestTarget())
    }

    @Test
    fun `收藏带 externalId 时进解读页，链接取实际打开地址`() {
        val item = FavoriteItem(
            url = "https://www.producthunt.com/posts/x",
            title = "Prelint",
            source = "producthunt",
            openUrl = "https://www.producthunt.com/products/prelint",
            externalId = "1200627",
        )
        assertEquals("https://www.producthunt.com/products/prelint", item.digestTarget()?.url)
    }
}
