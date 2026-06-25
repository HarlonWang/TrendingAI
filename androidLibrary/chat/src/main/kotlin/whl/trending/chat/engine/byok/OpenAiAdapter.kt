package whl.trending.chat.engine.byok

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * OpenAI `/chat/completions` 兼容协议适配：纯解析部分（流式 delta、/models 列表）。
 * 请求体构建见 [whl.trending.chat.engine.byok.ByokChatEngine]。
 */
object OpenAiAdapter {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable private data class StreamChunk(val choices: List<Choice> = emptyList())
    @Serializable private data class Choice(val delta: Delta = Delta())
    @Serializable private data class Delta(val content: String? = null)

    @Serializable private data class ModelsResponse(val data: List<Model> = emptyList())
    @Serializable private data class Model(val id: String)

    /** 从一个流式事件提取新增文本；`[DONE]`/role 帧/异常 JSON 均返回 null。 */
    fun deltaFrom(event: ServerSentEvent): String? {
        val data = event.data
        if (data == "[DONE]") return null
        val chunk = runCatching { json.decodeFromString<StreamChunk>(data) }.getOrNull() ?: return null
        return chunk.choices.firstOrNull()?.delta?.content?.takeIf { it.isNotEmpty() }
    }

    /** 解析 `GET {baseUrl}/models` 响应为模型 id 列表。 */
    fun parseModels(body: String): List<String> =
        runCatching { json.decodeFromString<ModelsResponse>(body).data.map { it.id } }
            .getOrElse { emptyList() }
}
