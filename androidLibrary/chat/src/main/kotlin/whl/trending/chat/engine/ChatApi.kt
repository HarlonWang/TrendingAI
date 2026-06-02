package whl.trending.chat.engine

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale
import whl.trending.ai.chat.ChatContext
import whl.trending.ai.data.local.AppLanguage
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.chat.model.ChatMessage
import whl.trending.chat.model.Role

/**
 * 正式聊天引擎：POST 到 api.trendingai.cn，后端转 ChatGPT，返回整段 Markdown（非流式）。
 *
 * - 透传 `X-Install-Id`（后端限流维度）
 * - 透传 `lang`（由 [AppLanguage] 解析，仅 zh 用中文，其余 en）
 * - 429 → [QuotaExceededException]
 */
class ChatApi(
    private val baseUrl: String = "https://api.trendingai.cn/api",
) : ChatEngine {

    @Serializable
    private data class WireMessage(val role: String, val content: String)

    @Serializable
    private data class ChatRequest(
        val messages: List<WireMessage>,
        val lang: String,
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
        val response: HttpResponse = client.post("$baseUrl/chat") {
            header("X-Install-Id", globalSettingsManager.getOrCreateInstallId())
            contentType(ContentType.Application.Json)
            setBody(
                ChatRequest(
                    messages = history.map {
                        WireMessage(
                            role = if (it.role == Role.USER) "user" else "assistant",
                            content = it.content,
                        )
                    },
                    lang = resolveLang(),
                    context = context?.let {
                        WireContext(it.title, it.summary, it.sourceUrl)
                    },
                ),
            )
        }

        when (response.status) {
            HttpStatusCode.OK -> return response.body<ChatResponse>().content
            HttpStatusCode.TooManyRequests -> throw QuotaExceededException()
            else -> throw IllegalStateException("Chat API error: ${response.status}")
        }
    }

    /** 仅 [AppLanguage.CHINESE] 或跟随系统且系统为中文时用 zh，其余 en（与后端默认一致）。 */
    private suspend fun resolveLang(): String {
        val appLang = globalSettingsManager.appLanguage.first()
        return appLang.isoCode
            ?: if (Locale.getDefault().language == "zh") "zh" else "en"
    }
}
