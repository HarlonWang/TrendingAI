package whl.trending.chat.engine.byok

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * SSE 帧解析器 [SseAccumulator] 纯函数单测。
 * 重点覆盖半行/粘包、CRLF、多行 data、注释、命名事件——直连流式最易出 bug 处。
 */
class SseAccumulatorTest {

    private fun feedAll(vararg chunks: String): List<ServerSentEvent> {
        val acc = SseAccumulator()
        val out = mutableListOf<ServerSentEvent>()
        chunks.forEach { out += acc.feed(it) }
        return out
    }

    @Test fun single_event_in_one_chunk() {
        val events = feedAll("data: hello\n\n")
        assertEquals(1, events.size)
        assertEquals("hello", events[0].data)
    }

    @Test fun data_split_across_chunks_is_glued() {
        // 半行/粘包：一个 data 被网络拆成两块
        val events = feedAll("data: hel", "lo\n\n")
        assertEquals(listOf("hello"), events.map { it.data })
    }

    @Test fun crlf_line_endings_are_handled() {
        val events = feedAll("data: hi\r\n\r\n")
        assertEquals(listOf("hi"), events.map { it.data })
    }

    @Test fun multiple_data_lines_joined_with_newline() {
        val events = feedAll("data: line1\ndata: line2\n\n")
        assertEquals(listOf("line1\nline2"), events.map { it.data })
    }

    @Test fun comment_lines_are_ignored() {
        // 以冒号开头的是注释（keep-alive 心跳），不应产生事件
        val events = feedAll(": keep-alive\n\ndata: real\n\n")
        assertEquals(listOf("real"), events.map { it.data })
    }

    @Test fun named_event_is_captured() {
        val events = feedAll("event: content_block_delta\ndata: {\"x\":1}\n\n")
        assertEquals(1, events.size)
        assertEquals("content_block_delta", events[0].event)
        assertEquals("{\"x\":1}", events[0].data)
    }

    @Test fun multiple_events_in_one_chunk() {
        val events = feedAll("data: a\n\ndata: b\n\ndata: c\n\n")
        assertEquals(listOf("a", "b", "c"), events.map { it.data })
    }

    @Test fun value_without_leading_space_after_colon() {
        // 规范允许冒号后无空格
        val events = feedAll("data:nospace\n\n")
        assertEquals(listOf("nospace"), events.map { it.data })
    }

    @Test fun no_event_dispatched_without_blank_line() {
        // 未遇空行不应吐出事件（边界未到）
        val events = feedAll("data: incomplete\n")
        assertTrue(events.isEmpty())
    }

    @Test fun done_sentinel_passes_through_as_data() {
        // [DONE] 由上层适配器处理，解析器原样透传
        val events = feedAll("data: [DONE]\n\n")
        assertEquals(listOf("[DONE]"), events.map { it.data })
    }
}
