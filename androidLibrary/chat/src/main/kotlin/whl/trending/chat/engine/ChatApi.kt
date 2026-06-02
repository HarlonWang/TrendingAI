package whl.trending.chat.engine

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import whl.trending.ai.chat.ChatContext
import whl.trending.chat.model.ChatMessage
import whl.trending.chat.model.Role

/**
 * 正式聊天引擎：POST 到 api.trendingai.cn，后端转 ChatGPT，返回整段 Markdown（非流式）。
 *
 * 注意：端点路径与请求/响应契约待后端定稿，这里先按合理结构编码，后续按实际接口调整。
 */
class ChatApi(
    private val baseUrl: String = "https://api.trendingai.cn/api",
) : ChatEngine {

    @Serializable
    private data class WireMessage(val role: String, val content: String)

    @Serializable
    private data class ChatRequest(
        val messages: List<WireMessage>,
        val context: WireContext? = null,
    )

    @Serializable
    private data class WireContext(
        val title: String,
        val summary: String? = null,
        val sourceUrl: String? = null,
    )

    @Serializable
    private data class ChatResponse(val content: String)

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            // 非流式等待较久，放宽超时
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 15_000
        }
    }

    override suspend fun send(history: List<ChatMessage>, context: ChatContext?): String {
        val response: ChatResponse = client.post("$baseUrl/chat") {
            contentType(ContentType.Application.Json)
            setBody(
                ChatRequest(
                    messages = history.map {
                        WireMessage(
                            role = if (it.role == Role.USER) "user" else "assistant",
                            content = it.content,
                        )
                    },
                    context = context?.let {
                        WireContext(it.title, it.summary, it.sourceUrl)
                    },
                ),
            )
        }.body()
        return response.content
    }
}
