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
import io.ktor.client.statement.bodyAsText
import android.util.Log
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale
import whl.trending.ai.chat.ChatContext
import whl.trending.ai.data.local.AppLanguage
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.chat.model.ChatError
import whl.trending.chat.model.ChatMessage
import whl.trending.chat.model.Role

private const val TAG = "ChatApi"

/**
 * 正式聊天引擎：POST 到 api.trendingai.cn，后端转 ChatGPT，返回整段 Markdown（非流式）。
 *
 * - 透传 `X-Install-Id`（后端限流维度）
 * - 透传 `lang`（由 [AppLanguage] 解析，仅 zh 用中文，其余 en）
 * - 所有失败（HTTP 非 2xx / 传输异常）统一归类为 [ChatException]，由 [ChatErrors] 分类
 */
class ChatApi(
    private val baseUrl: String = "https://api.trendingai.cn/api",
) : ChatEngine {

    companion object {
        /**
         * App 级共享实例：全进程仅一个 HttpClient，常驻至进程结束，无需 close。
         * 各会话线（keyed ChatViewModel）共用同一 engine，避免反复新建且从不关闭的泄漏。
         */
        val shared: ChatEngine by lazy { ChatApi() }
    }

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

    @Serializable
    private data class ErrorResponse(val error: String? = null, val code: String? = null)

    private val json = Json { ignoreUnknownKeys = true }

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpTimeout) {
            // 非流式等待较久，放宽超时
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 15_000
            // OkHttp 引擎默认 readTimeout=10s，会在等响应头阶段掐断慢的非流式回复，必须显式放宽
            socketTimeoutMillis = 60_000
        }
    }

    /** 非流式引擎：整段回复一次性 emit（详见 [ChatEngine.send] 约定）。 */
    override fun send(history: List<ChatMessage>, context: ChatContext?): Flow<String> = flow {
        emit(request(history, context))
    }

    private suspend fun request(history: List<ChatMessage>, context: ChatContext?): String {
        try {
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

            if (response.status == HttpStatusCode.OK) {
                return response.body<ChatResponse>().content
            }

            // 非 2xx：取服务端 error 文案 + 机器码，按状态码分类
            val raw = runCatching { response.bodyAsText() }.getOrNull()
            val parsed = raw?.let { runCatching { json.decodeFromString<ErrorResponse>(it) }.getOrNull() }
            val bodyError = parsed?.error ?: raw
            throw ChatException(ChatErrors.forStatus(response.status.value, parsed?.code, bodyError))
        } catch (e: ChatException) {
            logFailure(e.error)
            throw e
        } catch (e: CancellationException) {
            throw e // 不吞协程取消
        } catch (e: Throwable) {
            // 传输异常（超时/断网等）
            val error = ChatErrors.forThrowable(e)
            logFailure(error)
            throw ChatException(error)
        }
    }

    private fun logFailure(error: ChatError) {
        Log.w(
            TAG,
            "send failed: category=${error.category} code=${error.code} " +
                "status=${error.httpStatus} detail=${error.detail}",
        )
    }

    /** 仅 [AppLanguage.CHINESE] 或跟随系统且系统为中文时用 zh，其余 en（与后端默认一致）。 */
    private suspend fun resolveLang(): String {
        val appLang = globalSettingsManager.appLanguage.first()
        return appLang.isoCode
            ?: if (Locale.getDefault().language == "zh") "zh" else "en"
    }
}
