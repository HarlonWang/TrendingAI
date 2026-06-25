package whl.trending.chat.engine.byok

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.get
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readUTF8Line
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import whl.trending.ai.chat.ChatContext
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.chat.engine.ChatEngine
import whl.trending.chat.engine.ChatErrors
import whl.trending.chat.engine.ChatException
import whl.trending.chat.model.ChatMessage
import whl.trending.chat.model.Role

private const val TAG = "ByokChatEngine"
private const val ANTHROPIC_VERSION = "2023-06-01"
private const val ANTHROPIC_MAX_TOKENS = 4096

/**
 * BYOK 直连引擎：用用户自己的 key/baseUrl/model 直接请求 OpenAI 兼容或 Anthropic 端点，
 * SSE 流式逐字回复。失败统一归类为 [ChatException]（401/403 → AUTH）。
 */
class ByokChatEngine(private val config: ByokConfig) : ChatEngine {

    override fun send(history: List<ChatMessage>, context: ChatContext?): Flow<String> = flow {
        val lang = resolveLang()
        try {
            when (config.provider) {
                ByokProvider.OPENAI_COMPATIBLE -> streamOpenAi(history, context, lang)
                ByokProvider.ANTHROPIC -> streamAnthropic(history, context, lang)
            }
        } catch (e: ChatException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.w(TAG, "stream failed", e)
            throw ChatException(ChatErrors.forThrowable(e))
        }
    }

    // ---- OpenAI 兼容 ----

    @Serializable private data class OaMessage(val role: String, val content: String)
    @Serializable private data class OaRequest(
        val model: String,
        val stream: Boolean = true,
        val messages: List<OaMessage>,
    )

    private suspend fun FlowCollector<String>.streamOpenAi(
        history: List<ChatMessage>,
        context: ChatContext?,
        lang: String,
    ) {
        val messages = buildList {
            add(OaMessage("system", ByokPrompt.system(context, lang)))
            history.forEach { add(OaMessage(it.role.wire(), it.content)) }
        }
        val body = json.encodeToString(OaRequest(model = config.model, messages = messages))
        streamSse(
            url = ByokUrls.chatCompletions(config.baseUrl),
            body = body,
            applyAuth = { header("Authorization", "Bearer ${config.apiKey}") },
            extract = { OpenAiAdapter.deltaFrom(it) },
        )
    }

    // ---- Anthropic ----

    @Serializable private data class AnMessage(val role: String, val content: String)
    @Serializable private data class AnRequest(
        val model: String,
        val stream: Boolean = true,
        @SerialName("max_tokens") val maxTokens: Int = ANTHROPIC_MAX_TOKENS,
        val system: String,
        val messages: List<AnMessage>,
    )

    private suspend fun FlowCollector<String>.streamAnthropic(
        history: List<ChatMessage>,
        context: ChatContext?,
        lang: String,
    ) {
        // Anthropic：system 顶层独立，messages 仅 user/assistant
        val messages = history.map { AnMessage(it.role.wire(), it.content) }
        val body = json.encodeToString(
            AnRequest(model = config.model, system = ByokPrompt.system(context, lang), messages = messages),
        )
        streamSse(
            url = ByokUrls.anthropicMessages(config.baseUrl),
            body = body,
            applyAuth = {
                header("x-api-key", config.apiKey)
                header("anthropic-version", ANTHROPIC_VERSION)
            },
            extract = { AnthropicAdapter.deltaFrom(it) },
        )
    }

    // ---- 共用 SSE 读取 ----

    private suspend fun FlowCollector<String>.streamSse(
        url: String,
        body: String,
        applyAuth: io.ktor.client.request.HttpRequestBuilder.() -> Unit,
        extract: (ServerSentEvent) -> String?,
    ) {
        client.prepareRequest(url) {
            method = HttpMethod.Post
            contentType(ContentType.Application.Json)
            applyAuth()
            setBody(body)
        }.execute { response ->
            if (!response.status.isSuccess()) {
                val raw = runCatching { response.bodyAsText() }.getOrNull()
                throw ChatException(ChatErrors.forStatus(response.status.value, code = null, bodyError = raw))
            }
            val channel = response.bodyAsChannel()
            val acc = SseAccumulator()
            while (true) {
                val line = channel.readUTF8Line() ?: break
                // 复用已测的帧解析：逐行喂入（补回行尾换行作为分隔）
                acc.feed(line + "\n").forEach { event ->
                    extract(event)?.let { emit(it) }
                }
            }
        }
    }

    private fun Role.wire(): String = if (this == Role.USER) "user" else "assistant"

    private suspend fun resolveLang(): String {
        val appLang = globalSettingsManager.appLanguage.first()
        return appLang.isoCode ?: if (Locale.getDefault().language == "zh") "zh" else "en"
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** 流式专用 client：放宽逐字节间隔超时，整段请求不设硬上限。 */
        private val client: HttpClient by lazy {
            HttpClient(OkHttp) {
                install(HttpTimeout) {
                    // 流式整段可能较久；按"逐字节间隔"控超时（socket），整体上限给足
                    requestTimeoutMillis = 600_000
                    connectTimeoutMillis = 15_000
                    socketTimeoutMillis = 60_000
                }
            }
        }

        /**
         * 拉取模型列表（顺带充当连接测试）：成功返回模型 id 列表，失败抛 [ChatException]（含 AUTH 分类）。
         */
        suspend fun fetchModels(config: ByokConfig): List<String> {
            return try {
                when (config.provider) {
                    ByokProvider.OPENAI_COMPATIBLE -> {
                        val resp = client.get(ByokUrls.openAiModels(config.baseUrl)) {
                            header("Authorization", "Bearer ${config.apiKey}")
                        }
                        if (!resp.status.isSuccess()) {
                            throw ChatException(ChatErrors.forStatus(resp.status.value, null, runCatching { resp.bodyAsText() }.getOrNull()))
                        }
                        OpenAiAdapter.parseModels(resp.bodyAsText())
                    }
                    ByokProvider.ANTHROPIC -> {
                        val resp = client.get(ByokUrls.anthropicModels(config.baseUrl)) {
                            header("x-api-key", config.apiKey)
                            header("anthropic-version", ANTHROPIC_VERSION)
                        }
                        if (!resp.status.isSuccess()) {
                            throw ChatException(ChatErrors.forStatus(resp.status.value, null, runCatching { resp.bodyAsText() }.getOrNull()))
                        }
                        AnthropicAdapter.parseModels(resp.bodyAsText())
                    }
                }
            } catch (e: ChatException) {
                throw e
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                throw ChatException(ChatErrors.forThrowable(e))
            }
        }
    }
}
