package whl.trending.chat.engine.byok

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** OpenAI 兼容协议的纯解析单测：流式 delta 提取 + /models 列表解析。 */
class OpenAiAdapterTest {

    private val adapter = OpenAiAdapter

    @Test fun extracts_content_delta() {
        val data = """{"choices":[{"delta":{"content":"Hello"},"index":0}]}"""
        assertEquals("Hello", adapter.deltaFrom(ServerSentEvent(null, data)))
    }

    @Test fun done_sentinel_returns_null() {
        assertNull(adapter.deltaFrom(ServerSentEvent(null, "[DONE]")))
    }

    @Test fun role_only_delta_returns_null() {
        // 首帧常只含 role、无 content
        val data = """{"choices":[{"delta":{"role":"assistant"},"index":0}]}"""
        assertNull(adapter.deltaFrom(ServerSentEvent(null, data)))
    }

    @Test fun empty_choices_returns_null() {
        assertNull(adapter.deltaFrom(ServerSentEvent(null, """{"choices":[]}""")))
    }

    @Test fun malformed_json_returns_null_not_throws() {
        assertNull(adapter.deltaFrom(ServerSentEvent(null, "not json")))
    }

    @Test fun parses_models_list() {
        val body = """{"object":"list","data":[{"id":"gpt-4o","object":"model"},{"id":"gpt-4o-mini"}]}"""
        assertEquals(listOf("gpt-4o", "gpt-4o-mini"), adapter.parseModels(body))
    }

    @Test fun parses_empty_models_list() {
        assertTrue(adapter.parseModels("""{"object":"list","data":[]}""").isEmpty())
    }
}
