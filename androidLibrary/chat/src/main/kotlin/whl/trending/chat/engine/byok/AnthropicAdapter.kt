package whl.trending.chat.engine.byok

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Anthropic 原生 `/v1/messages` 协议适配：纯解析部分（流式 text_delta、/v1/models 列表）。
 * 请求体构建见 [whl.trending.chat.engine.byok.ByokChatEngine]。
 */
object AnthropicAdapter {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable private data class StreamEvent(val type: String? = null, val delta: Delta? = null)
    @Serializable private data class Delta(val type: String? = null, val text: String? = null)

    @Serializable private data class ModelsResponse(val data: List<Model> = emptyList())
    @Serializable private data class Model(val id: String)

    /** 仅 `content_block_delta` 的 `text_delta` 产出文本；其余事件类型/异常 JSON 返回 null。 */
    fun deltaFrom(event: ServerSentEvent): String? {
        val parsed = runCatching { json.decodeFromString<StreamEvent>(event.data) }.getOrNull() ?: return null
        if (parsed.type != "content_block_delta") return null
        val delta = parsed.delta ?: return null
        if (delta.type != "text_delta") return null
        return delta.text?.takeIf { it.isNotEmpty() }
    }

    /** 解析 `GET {baseUrl}/v1/models` 响应为模型 id 列表。 */
    fun parseModels(body: String): List<String> =
        runCatching { json.decodeFromString<ModelsResponse>(body).data.map { it.id } }
            .getOrElse { emptyList() }
}
