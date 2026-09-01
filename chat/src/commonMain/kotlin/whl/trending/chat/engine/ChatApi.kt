package whl.trending.chat.engine

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.preparePost
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import whl.trending.chat.ChatContext
import whl.trending.chat.host.chatHost
import whl.trending.chat.model.ChatModelsProvider
import whl.trending.chat.model.resolveEffectiveChatModel
import whl.trending.chat.ChatViewModel
import whl.trending.chat.core.logWarn
import whl.trending.chat.model.ChatError
import whl.trending.chat.model.ChatErrorCategory
import whl.trending.chat.model.ChatMessage
import whl.trending.chat.model.Role
import whl.trending.chat.model.SearchEvent

private const val TAG = "ChatApi"

/**
 * 正式聊天引擎：POST 到 api.trendingai.cn，后端转 ChatGPT，SSE 流式返回（`stream: true`）。
 *
 * - 透传 `X-Install-Id`（后端限流维度）
 * - 透传 `lang`（宿主解析，仅 zh 用中文，其余 en）
 * - 所有失败（HTTP 非 2xx / 传输异常 / 中途断流）统一归类为 [ChatException]，由 [ChatErrors] 分类
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

        /** 流式请求总时长上限：生成 2500~4000 字解读可能远超普通请求，放到 5 分钟 */
        private const val STREAM_REQUEST_TIMEOUT_MS = 300_000L

        /**
         * wire 序列化配置的唯一定义：请求编码（ContentNegotiation）与 wire 闸门测试
         * （ChatApiWireTest）共用同一实例——改这里的配置（如 encodeDefaults/explicitNulls）
         * 测试即变红。测试若自建配置相同的 Json 副本，配置漂移时闸门会静默失效。
         */
        internal val wireJson = Json { ignoreUnknownKeys = true }
    }

    /** content 两态：纯文本为 JSON string；末条带图 user 消息为 OpenAI 多模态 parts 数组 */
    @Serializable
    internal data class WireMessage(val role: String, val content: JsonElement)

    /** internal 供 wire 序列化测试直接构造；stream 故意**不带默认值**——
     *  encodeDefaults=false 下「值等于默认值的属性会被省略」，曾让 "stream":true 静默消失 */
    @Serializable
    internal data class ChatRequest(
        val messages: List<WireMessage>,
        val lang: String,
        val context: WireContext? = null,
        val model: String? = null,
        val stream: Boolean,
        // P2 联网搜索开关：默认 false 时被 encodeDefaults=false 省略，旧服务端零感知
        val search: Boolean = false,
    )

    @Serializable
    internal data class WireContext(
        val title: String,
        val summary: String? = null,
        val sourceUrl: String? = null,
    )

    @Serializable
    private data class ChatResponse(val content: String)

    @Serializable
    private data class ResearchCreateRequest(val topic: String, val lang: String)

    @Serializable
    private data class ResearchRunResponse(
        val id: String,
        val status: String,
        val report: String? = null,
        val error: String? = null,
        val model: String? = null,
    )

    @Serializable
    private data class ErrorResponse(
        val error: String? = null,
        val code: String? = null,
        val tier: String? = null,
    )

    private val json = wireJson

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 15_000
            // OkHttp 引擎默认 readTimeout=10s；流式场景它约束的是「相邻数据块间隔」，放宽到 60s
            socketTimeoutMillis = 60_000
        }
        chatHost.configureHttpAuth(this)
    }.also { chatHost.registerAuthorizedClient(it) }

    override suspend fun send(
        history: List<ChatMessage>,
        context: ChatContext?,
        onDelta: (String) -> Unit,
        search: Boolean,
        onSearch: (SearchEvent) -> Unit,
    ): String {
        val lang = resolveLang()
        val imagePlaceholder = if (lang == "zh") "[图片]" else "[image]"
        val maxImages = ChatViewModel.maxImagesPerMessage()
        return executeStreaming(path = "chat", onDelta = onDelta, onSearch = onSearch) {
            setBody(
                ChatRequest(
                    messages = history.mapIndexed { index, m ->
                        WireMessage(
                            role = if (m.role == Role.USER) "user" else "assistant",
                            content = ChatWire.buildContent(
                                message = m,
                                isLast = index == history.lastIndex,
                                imagePlaceholder = imagePlaceholder,
                                maxImages = maxImages,
                                readImageBytes = { path ->
                                    runCatching { FileSystem.SYSTEM.read(path.toPath()) { readByteArray() } }.getOrNull()
                                },
                            ),
                        )
                    },
                    lang = lang,
                    context = context?.let {
                        WireContext(it.title, it.summary, it.sourceUrl)
                    },
                    // 发送前按目录缓存解析实际生效模型（与选择器同一套判定），避免选择器未挂载时
                    // 把过期的 Pro 专属选择原样发出；未手选 / 选择失效解析为 null，此时字段被
                    // encodeDefaults=false 省略，默认模型由服务端定（客户端不复述模型 id）
                    model = resolveEffectiveChatModel(
                        ChatModelsProvider.cachedOrEmpty().models,
                        chatHost.currentChatModelChoice(),
                        chatHost.currentIsPro(),
                    ),
                    stream = true,
                    search = search,
                ),
            )
        }
    }

    override suspend fun createResearch(topic: String): String {
        val lang = resolveLang()
        return researchJson(HttpMethod.Post, "research") {
            contentType(ContentType.Application.Json)
            setBody(ResearchCreateRequest(topic = topic, lang = lang))
        }.id
    }

    override suspend fun pollResearch(id: String): whl.trending.chat.model.ResearchRun {
        val r = researchJson(HttpMethod.Get, "research/$id") {}
        return whl.trending.chat.model.ResearchRun(r.id, r.status, r.report, r.error, r.model)
    }

    /** research 的非流式 JSON 路径：错误分类与流式路径同一套（含 login_required/quota 语义） */
    private suspend fun researchJson(
        method: HttpMethod,
        path: String,
        configure: HttpRequestBuilder.() -> Unit,
    ): ResearchRunResponse {
        try {
            val sentAsLoggedIn = chatHost.isLoggedInNow()
            val response = client.request("$baseUrl/$path") {
                this.method = method
                header("X-Install-Id", chatHost.installId())
                timeout { requestTimeoutMillis = 30_000 }
                configure()
            }
            if (response.status != HttpStatusCode.OK) {
                throw toChatException(response, sentAsLoggedIn)
            }
            return json.decodeFromString<ResearchRunResponse>(response.bodyAsText())
        } catch (e: ChatException) {
            logFailure(path, e.error)
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            val error = ChatErrors.forThrowable(e)
            logFailure(path, error)
            throw ChatException(error)
        }
    }

    /**
     * 流式执行路径：POST → 非 2xx 走 JSON 错误分类；2xx SSE 逐行解析 delta/done；
     * 2xx 非 SSE（服务端降级非流式）整段作为一个 delta 兜底。
     * 流在 done 之前结束视为中途断流 → SERVER 可重试，已渲染部分由调用方丢弃。
     */
    private suspend fun executeStreaming(
        path: String,
        onDelta: (String) -> Unit,
        onSearch: (SearchEvent) -> Unit = {},
        configure: HttpRequestBuilder.() -> Unit,
    ): String {
        try {
            // 发送时的登录自认知：与 429 的 tier=anonymous 对照可识别「token 缺失/被拒被静默降级」
            val sentAsLoggedIn = chatHost.isLoggedInNow()
            return client.preparePost("$baseUrl/$path") {
                header("X-Install-Id", chatHost.installId())
                contentType(ContentType.Application.Json)
                timeout { requestTimeoutMillis = STREAM_REQUEST_TIMEOUT_MS }
                configure()
            }.execute { response ->
                if (response.status != HttpStatusCode.OK) {
                    throw toChatException(response, sentAsLoggedIn)
                }
                if (response.contentType()?.match(ContentType.Text.EventStream) != true) {
                    // 服务端降级为非流式 JSON（spec 的 SSE 兜底路径）
                    val content = json.decodeFromString<ChatResponse>(response.bodyAsText()).content
                    onDelta(content)
                    return@execute content
                }
                val channel = response.bodyAsChannel()
                val full = StringBuilder()
                var done = false
                while (!done) {
                    val line = channel.readUTF8Line() ?: break
                    when (val event = ChatSse.parseLine(line)) {
                        is ChatSse.Event.Delta -> {
                            full.append(event.text)
                            onDelta(event.text)
                        }
                        is ChatSse.Event.Done -> done = true
                        is ChatSse.Event.SearchStarted -> onSearch(SearchEvent.Started)
                        is ChatSse.Event.SearchDone -> onSearch(SearchEvent.Done(event.query))
                        is ChatSse.Event.Source -> onSearch(SearchEvent.Source(event.title, event.url))
                        null -> Unit
                    }
                }
                if (!done) {
                    throw ChatException(
                        ChatError(
                            ChatErrorCategory.SERVER,
                            code = ChatError.CODE_STREAM_INTERRUPTED,
                            detail = "stream ended before done event",
                        ),
                    )
                }
                full.toString()
            }
        } catch (e: ChatException) {
            logFailure(path, e.error)
            throw e
        } catch (e: CancellationException) {
            throw e // 不吞协程取消
        } catch (e: Throwable) {
            // 传输异常（超时/断网/流中断等）
            val error = ChatErrors.forThrowable(e)
            logFailure(path, error)
            throw ChatException(error)
        }
    }

    /** 非 2xx：取服务端 error 文案 + 机器码，按状态码分类 */
    private suspend fun toChatException(response: HttpResponse, sentAsLoggedIn: Boolean): ChatException {
        val raw = runCatching { response.bodyAsText() }.getOrNull()
        val parsed = raw?.let { runCatching { json.decodeFromString<ErrorResponse>(it) }.getOrNull() }
        val bodyError = parsed?.error ?: raw
        return ChatException(
            ChatErrors.markAuthDegraded(
                ChatErrors.forStatus(response.status.value, parsed?.code, bodyError, parsed?.tier),
                sentAsLoggedIn,
            ),
        )
    }

    private fun logFailure(path: String, error: ChatError) {
        logWarn(
            TAG,
            "$path failed: category=${error.category} code=${error.code} " +
                "status=${error.httpStatus} detail=${error.detail}",
        )
    }

    /** 请求语言由宿主解析（app 语言设置 + 系统语言），仅 "zh"/"en" 两值（与后端默认一致）。 */
    private suspend fun resolveLang(): String = chatHost.requestLang()
}
