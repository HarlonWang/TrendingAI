package whl.trending.chat.engine

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlinx.serialization.encodeToString

/**
 * 请求方向的 wire 序列化断言。
 *
 * 全部用 [ChatApi.wireJson]——生产环境 ContentNegotiation 装的同一实例，而非自建配置相同的
 * 副本：副本在生产配置漂移（如误开 encodeDefaults）时照样绿，闸门形同虚设。
 *
 * 回归背景：kotlinx.serialization 默认 encodeDefaults=false，带默认值的属性在值等于默认值时
 * 会被整个省略——`stream: Boolean = true` 曾因此从请求体里消失，服务端 `body.stream === true`
 * 严格判断收不到字段就走旧 JSON 路径，chat 流式静默失效。此测试保证 stream 字段必定出现在线上。
 */
class ChatApiWireTest {

    @Test
    fun `chat 请求体必须序列化出 stream true`() {
        val body = ChatApi.wireJson.encodeToString(
            ChatApi.ChatRequest(messages = emptyList(), lang = "zh", stream = true),
        )
        assertContains(body, "\"stream\":true")
    }

    /**
     * 同一条 encodeDefaults=false 规则，这次是**指望**它省略：未手选模型时 model 解析为 null，
     * 字段必须整个消失，让服务端按 tier 决定默认模型（免费白名单 / Pro 目录都回落 DEFAULT_MODEL）。
     * 若哪天开了 encodeDefaults 或改用 explicitNulls 发出 `"model":null`，后端仍会兜底，
     * 但「客户端不复述默认模型 id」的约定就断了——此测试是那道闸。
     */
    @Test
    fun `未手选模型时请求体不带 model 字段`() {
        val body = ChatApi.wireJson.encodeToString(
            ChatApi.ChatRequest(messages = emptyList(), lang = "zh", model = null, stream = true),
        )
        assertFalse(body.contains("\"model\""), "未手选却发了 model 字段: $body")
    }

    @Test
    fun `手选模型时请求体照常带 model 字段`() {
        val body = ChatApi.wireJson.encodeToString(
            ChatApi.ChatRequest(messages = emptyList(), lang = "zh", model = "gpt-6", stream = true),
        )
        assertContains(body, "\"model\":\"gpt-6\"")
    }
}
