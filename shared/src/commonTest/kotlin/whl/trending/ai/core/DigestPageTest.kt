package whl.trending.ai.core

import whl.trending.ai.data.model.FavoriteItem
import whl.trending.ai.data.model.PickItem
import whl.trending.ai.data.model.TrendingRepo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DigestPageTest {

    private fun favorite(externalId: String) = FavoriteItem(
        url = "https://example.com/article",
        title = "t",
        source = "hackernews",
        openUrl = "https://news.ycombinator.com/item?id=123",
        externalId = externalId,
    )

    @Test
    fun 数字id_讨论区链接按id拼接() {
        val page = favorite("123").toDigestPage()
        assertEquals("https://news.ycombinator.com/item?id=123", page.discussionUrl)
    }

    @Test
    fun 合成键id_回退收藏时记录的打开地址_Sourcery审查回归() {
        // 存量收藏经云同步后 externalId 可能是持久化的 `url:<url>` 合成键，
        // 拼进 item?id= 会生成废链接，必须回退 targetUrl
        val page = favorite("url:https://example.com/article").toDigestPage()
        assertEquals("https://news.ycombinator.com/item?id=123", page.discussionUrl)
    }

    @Test
    fun 空id_解析出合成键仍回退打开地址() {
        // externalId 空串时 resolvedExternalId 派生合成键（非数字）→ 同样走回退
        val page = favorite("").toDigestPage()
        assertEquals("https://news.ycombinator.com/item?id=123", page.discussionUrl)
    }

    @Test
    fun GitHub收藏_无externalId时从url反解ownerRepo_无讨论页() {
        val page = FavoriteItem(url = "https://github.com/owner/repo/?tab=readme-ov-file", title = "t", source = "github").toDigestPage()
        assertEquals("owner/repo", page.externalId)
        assertEquals("owner" to "repo", page.githubOwnerRepo)
        assertNull(page.discussionUrl)
        assertFalse(page.isSelfPost)
    }

    @Test
    fun GitHub_url带query或fragment_反解时剥掉() {
        val page = PickItem(source = "github", externalId = "", title = "b", url = "https://github.com/a/b?tab=readme-ov-file#readme").toDigestPage()
        assertEquals("a/b", page.externalId)
        assertEquals("a" to "b", page.githubOwnerRepo)
    }

    @Test
    fun GitHub收藏_url非github域_合成键不给README按钮() {
        val page = FavoriteItem(url = "https://example.com/x/y", title = "t", source = "github").toDigestPage()
        assertTrue(page.externalId.startsWith("url:"))
        assertNull(page.githubOwnerRepo)
    }

    @Test
    fun GitHub_Picks条目_externalId从url反解() {
        val page = PickItem(source = "github", externalId = "", title = "b", url = "https://github.com/a/b").toDigestPage()
        assertEquals("a/b", page.externalId)
        assertEquals("a/b", page.title)
    }

    @Test
    fun GitHub_Trending条目_标题为ownerRepo_带语言与star() {
        val repo = TrendingRepo(author = "a", repoName = "b", url = "https://github.com/a/b", description = "d", language = "Rust", stars = 1200, currentPeriodStars = 30)
        val page = repo.toDigestPage(summary = "s")
        assertEquals("a/b", page.title)
        assertEquals("a/b", page.externalId)
        assertEquals("Rust", page.language)
        assertEquals(1200, page.stars)
        assertEquals(30, page.score)
        assertEquals("s", page.summary)
        assertTrue(page.isGithub)
    }

    @Test
    fun PH收藏_讨论页取收藏时记录的打开地址() {
        val page = FavoriteItem(url = "https://example.com", title = "t", source = "producthunt", openUrl = "https://www.producthunt.com/posts/x", externalId = "42").toDigestPage()
        assertEquals("42", page.externalId)
        assertEquals("https://www.producthunt.com/posts/x", page.discussionUrl)
        assertTrue(page.isProductHunt)
        assertFalse(page.isSelfPost)
    }

    @Test
    fun PH_Picks条目_讨论页取phUrl() {
        val page = PickItem(source = "producthunt", externalId = "42", title = "t", url = "https://example.com", phUrl = "https://www.producthunt.com/posts/x").toDigestPage()
        assertEquals("https://www.producthunt.com/posts/x", page.discussionUrl)
    }

    @Test
    fun HN自建帖_原文即讨论页() {
        val page = DigestPage(source = "hackernews", externalId = "1", title = "t", url = "https://news.ycombinator.com/item?id=1", discussionUrl = "https://news.ycombinator.com/item?id=1")
        assertTrue(page.isSelfPost)
    }
}
