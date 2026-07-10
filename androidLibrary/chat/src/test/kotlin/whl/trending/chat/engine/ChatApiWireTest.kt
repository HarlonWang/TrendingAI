package whl.trending.chat.engine

import kotlin.test.Test
import kotlin.test.assertContains
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 请求方向的 wire 序列化断言。
 *
 * 回归背景：kotlinx.serialization 默认 encodeDefaults=false，带默认值的属性在值等于默认值时
 * 会被整个省略——`stream: Boolean = true` 曾因此从请求体里消失，服务端 `body.stream === true`
 * 严格判断收不到字段就走旧 JSON 路径，chat 流式静默失效。此测试保证 stream 字段必定出现在线上。
 */
class ChatApiWireTest {

    @Test
    fun `chat 请求体必须序列化出 stream true`() {
        val body = Json { ignoreUnknownKeys = true }.encodeToString(
            ChatApi.ChatRequest(messages = emptyList(), lang = "zh", stream = true),
        )
        assertContains(body, "\"stream\":true")
    }
}
