package whl.trending.ai.core.platform

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UrlPolicyTest {

    @Test
    fun `producthunt redirect short link is protected`() {
        assertTrue(isCloudflareProtectedUrl("https://www.producthunt.com/r/XBX6EKSLADLB4V?utm_campaign=producthunt-api"))
    }

    @Test
    fun `producthunt post page is protected`() {
        assertTrue(isCloudflareProtectedUrl("https://www.producthunt.com/products/context-dev"))
    }

    @Test
    fun `bare producthunt host without www is protected`() {
        assertTrue(isCloudflareProtectedUrl("https://producthunt.com/posts/foo"))
    }

    @Test
    fun `host matching is case insensitive`() {
        assertTrue(isCloudflareProtectedUrl("https://WWW.PRODUCTHUNT.COM/r/ABC"))
    }

    @Test
    fun `github url is not protected`() {
        assertFalse(isCloudflareProtectedUrl("https://github.com/anthropics/claude-code"))
    }

    @Test
    fun `hacker news url is not protected`() {
        assertFalse(isCloudflareProtectedUrl("https://news.ycombinator.com/item?id=1"))
    }

    @Test
    fun `lookalike host with suffix is not protected`() {
        assertFalse(isCloudflareProtectedUrl("https://producthunt.com.evil.example/r/ABC"))
        assertFalse(isCloudflareProtectedUrl("https://notproducthunt.com/r/ABC"))
    }

    @Test
    fun `url with port and userinfo still resolves host`() {
        assertTrue(isCloudflareProtectedUrl("https://www.producthunt.com:443/r/ABC"))
    }

    @Test
    fun `blank or malformed url is not protected`() {
        assertFalse(isCloudflareProtectedUrl(""))
        assertFalse(isCloudflareProtectedUrl("not a url"))
    }
}
