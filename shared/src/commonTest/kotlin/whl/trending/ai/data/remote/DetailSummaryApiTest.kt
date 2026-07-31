package whl.trending.ai.data.remote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** SSE 行解析：协议与 Worker 端 lib/sse.js 一一对应，解错会让解读页整页空白 */
class DigestSseTest {

    @Test
    fun `解析增量`() {
        val event = DigestSse.parseLine("""data: {"delta":"### 这是什么"}""")
        assertEquals(DigestSse.Event.Delta("### 这是什么"), event)
    }

    @Test
    fun `解析收尾并识别缓存命中`() {
        assertEquals(DigestSse.Event.Done(cached = true), DigestSse.parseLine("""data: {"done":true,"cached":true}"""))
        assertEquals(DigestSse.Event.Done(cached = false), DigestSse.parseLine("""data: {"done":true}"""))
    }

    @Test
    fun `未知事件、注释与脏行一律忽略（向前兼容服务端新增事件）`() {
        assertNull(DigestSse.parseLine(""": keep-alive"""))
        assertNull(DigestSse.parseLine(""))
        assertNull(DigestSse.parseLine("data: "))
        assertNull(DigestSse.parseLine("data: {不是 json"))
        assertNull(DigestSse.parseLine("""data: {"search":{"state":"started"}}"""))
    }

    @Test
    fun `空字符串增量按增量处理，不当成脏行`() {
        assertEquals(DigestSse.Event.Delta(""), DigestSse.parseLine("""data: {"delta":""}"""))
    }
}

/** 错误分类：只有登录闸该给登录 CTA，配额与素材不足都不该让用户以为「重试就好」 */
class DigestErrorClassifyTest {

    @Test
    fun `403 login_required 为登录闸`() {
        assertEquals(DigestError.LoginRequired, classifyDigestError(403, "login_required"))
    }

    @Test
    fun `429 区分个人额度与全局池`() {
        assertEquals(DigestError.Quota(global = false), classifyDigestError(429, "quota_device"))
        assertEquals(DigestError.Quota(global = true), classifyDigestError(429, "quota_global"))
    }

    @Test
    fun `400 no_content 为素材不足`() {
        assertEquals(DigestError.NoContent, classifyDigestError(400, "no_content"))
    }

    @Test
    fun `404 为条目已不在榜单`() {
        assertEquals(DigestError.NotFound, classifyDigestError(404, null))
    }

    @Test
    fun `其余状态码可重试；机器码缺失也不崩`() {
        assertTrue(classifyDigestError(502, "upstream_error") is DigestError.Retryable)
        assertTrue(classifyDigestError(500, null) is DigestError.Retryable)
        // 403 但不是登录闸（如鉴权被代理拦下）不给登录 CTA，避免登录后仍然失败的死循环
        assertTrue(classifyDigestError(403, null) is DigestError.Retryable)
        // 400 但不是素材不足（如入参非法）同样走重试路径
        assertTrue(classifyDigestError(400, "invalid_request") is DigestError.Retryable)
    }
}
