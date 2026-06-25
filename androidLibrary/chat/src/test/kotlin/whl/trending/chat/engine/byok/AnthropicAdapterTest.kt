package whl.trending.chat.engine.byok

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Anthropic 原生协议的纯解析单测：流式 text_delta 提取 + /v1/models 列表解析。 */
class AnthropicAdapterTest {

    private val adapter = AnthropicAdapter

    @Test fun extracts_text_delta() {
        val data = """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hi"}}"""
        assertEquals("Hi", adapter.deltaFrom(ServerSentEvent("content_block_delta", data)))
    }

    @Test fun message_start_event_returns_null() {
        val data = """{"type":"message_start","message":{"id":"msg_1"}}"""
        assertNull(adapter.deltaFrom(ServerSentEvent("message_start", data)))
    }

    @Test fun ping_event_returns_null() {
        assertNull(adapter.deltaFrom(ServerSentEvent("ping", """{"type":"ping"}""")))
    }

    @Test fun non_text_delta_returns_null() {
        // 例如 input_json_delta（工具调用），非文本
        val data = """{"type":"content_block_delta","delta":{"type":"input_json_delta","partial_json":"{"}}"""
        assertNull(adapter.deltaFrom(ServerSentEvent("content_block_delta", data)))
    }

    @Test fun malformed_json_returns_null_not_throws() {
        assertNull(adapter.deltaFrom(ServerSentEvent("content_block_delta", "boom")))
    }

    @Test fun parses_models_list() {
        val body = """{"data":[{"id":"claude-opus-4-8","type":"model"},{"id":"claude-sonnet-4-6"}],"has_more":false}"""
        assertEquals(listOf("claude-opus-4-8", "claude-sonnet-4-6"), adapter.parseModels(body))
    }

    @Test fun parses_empty_models_list() {
        assertTrue(adapter.parseModels("""{"data":[]}""").isEmpty())
    }
}
