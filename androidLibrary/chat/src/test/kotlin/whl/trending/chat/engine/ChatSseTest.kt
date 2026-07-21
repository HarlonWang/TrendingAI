package whl.trending.chat.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** SSE 行解析纯函数：与 Worker 端 lib/sse.js 的事件协议一一对应。 */
class ChatSseTest {

    @Test
    fun `delta 事件解析出文本`() {
        val event = ChatSse.parseLine("""data: {"delta":"你好"}""")
        assertEquals(ChatSse.Event.Delta("你好"), event)
    }

    @Test
    fun `done 事件解析为结束信号`() {
        val event = ChatSse.parseLine("""data: {"done":true}""")
        assertEquals(ChatSse.Event.Done(cached = false), event)
    }

    @Test
    fun `done 事件携带 cached 元信息（缓存命中一次性推完）`() {
        val event = ChatSse.parseLine("""data: {"done":true,"cached":true}""")
        assertEquals(ChatSse.Event.Done(cached = true), event)
    }

    @Test
    fun `非 data 前缀行忽略`() {
        assertNull(ChatSse.parseLine(""))
        assertNull(ChatSse.parseLine(": keep-alive"))
        assertNull(ChatSse.parseLine("event: message"))
    }

    @Test
    fun `脏行与非法 JSON 忽略不抛`() {
        assertNull(ChatSse.parseLine("data: not-json"))
        assertNull(ChatSse.parseLine("data: {\"unknown\":1}"))
    }

    @Test
    fun `delta 内容含特殊字符与换行`() {
        val event = ChatSse.parseLine("""data: {"delta":"a\nb \"c\""}""")
        assertEquals(ChatSse.Event.Delta("a\nb \"c\""), event)
    }
}

// ---------------------------------------------------------------------------
// P2 web search：服务端 sse.js 新增的搜索事件行
// ---------------------------------------------------------------------------

class ChatSseSearchTest {

    @Test
    fun `search started 行`() {
        val e = ChatSse.parseLine("""data: {"search":{"state":"started"}}""")
        assertEquals(ChatSse.Event.SearchStarted, e)
    }

    @Test
    fun `search done 行带 query`() {
        val e = ChatSse.parseLine("""data: {"search":{"state":"done","query":"kotlin 2.4"}}""")
        assertEquals(ChatSse.Event.SearchDone("kotlin 2.4"), e)
    }

    @Test
    fun `search done 行无 query`() {
        val e = ChatSse.parseLine("""data: {"search":{"state":"done"}}""")
        assertEquals(ChatSse.Event.SearchDone(null), e)
    }

    @Test
    fun `source 行`() {
        val e = ChatSse.parseLine("""data: {"source":{"title":"Kotlin","url":"https://kotlinlang.org"}}""")
        assertEquals(ChatSse.Event.Source("Kotlin", "https://kotlinlang.org"), e)
    }

    @Test
    fun `未知 search state 返回 null（向前兼容）`() {
        assertNull(ChatSse.parseLine("""data: {"search":{"state":"future"}}"""))
    }
}
