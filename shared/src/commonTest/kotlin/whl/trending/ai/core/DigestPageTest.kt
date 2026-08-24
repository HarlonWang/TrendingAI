package whl.trending.ai.core

import whl.trending.ai.data.model.FavoriteItem
import kotlin.test.Test
import kotlin.test.assertEquals

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
        assertEquals("https://news.ycombinator.com/item?id=123", page.hnUrl)
    }

    @Test
    fun 合成键id_回退收藏时记录的打开地址_Sourcery审查回归() {
        // 存量收藏经云同步后 externalId 可能是持久化的 `url:<url>` 合成键，
        // 拼进 item?id= 会生成废链接，必须回退 targetUrl
        val page = favorite("url:https://example.com/article").toDigestPage()
        assertEquals("https://news.ycombinator.com/item?id=123", page.hnUrl)
    }

    @Test
    fun 空id_解析出合成键仍回退打开地址() {
        // externalId 空串时 resolvedExternalId 派生合成键（非数字）→ 同样走回退
        val page = favorite("").toDigestPage()
        assertEquals("https://news.ycombinator.com/item?id=123", page.hnUrl)
    }
}
