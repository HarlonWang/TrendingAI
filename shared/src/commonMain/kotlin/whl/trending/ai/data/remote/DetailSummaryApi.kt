package whl.trending.ai.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import whl.trending.ai.auth.globalAuthManager
import whl.trending.ai.core.platform.getUserAgent
import whl.trending.ai.data.local.globalSettingsManager

/** 一次解读请求的结果：全文 + 是否服务端缓存命中（缓存命中不计额，用于埋点区分） */
data class DigestResult(val markdown: String, val cached: Boolean)

/**
 * 解读失败的分类。UI 据此选卡片形态：只有 [LoginRequired] 给登录 CTA，
 * [NoContent] 引导看原文，其余给重试。
 */
sealed interface DigestError {
    /** 匿名请求未缓存条目：生成仅登录开放（服务端 403 login_required） */
    data object LoginRequired : DigestError

    /** 配额触顶（429）；[global] 为 true 表示全局池打满而非个人额度用尽 */
    data class Quota(val global: Boolean) : DigestError

    /** 素材不足以生成解读（400 no_content），如无正文亦无评论的 HN 新帖 */
    data object NoContent : DigestError

    /** 条目不在榜单目录内（404） */
    data object NotFound : DigestError

    /** 其余服务端错误 / 传输失败 / 中途断流，一律可重试 */
    data class Retryable(val detail: String?) : DigestError
}

class DigestException(val error: DigestError) : Exception(error.toString())

/**
 * 榜单条目「AI 解读」接口（`POST /api/detail-summary`，SSE 流式）。
 *
 * 与 chat 模块的 ChatApi 走同一 endpoint 与同一事件协议（`data: {"delta"}` /
 * `data: {"done":true,"cached":bool}`），但那套实现在 Android-only 模块里且带
 * 会话语义；解读页是 shared 页面，故在 commonMain 另起一份最小实现——
 * 只解析 delta/done，忽略未知行（向前兼容服务端新增事件）。
 */
open class DetailSummaryApi(
    private val baseUrl: String = "https://api.trendingai.cn/api",
) {
    companion object {
        /** 全进程共享：解读页可能被反复进出，避免每次新建从不关闭的 HttpClient */
        val shared: DetailSummaryApi by lazy { DetailSummaryApi() }

        /** 生成一篇解读可能耗时数十秒，流式总时长放到 5 分钟（与 chat 同口径） */
        private const val STREAM_REQUEST_TIMEOUT_MS = 300_000L
    }

    @Serializable
    private data class DigestRequest(
        val source: String,
        @SerialName("external_id") val externalId: String,
        val lang: String,
    )

    @Serializable
    private data class ErrorResponse(
        val error: String? = null,
        val code: String? = null,
        val tier: String? = null,
    )

    private val json = Json { ignoreUnknownKeys = true }

    private val client = HttpClient {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = STREAM_REQUEST_TIMEOUT_MS
            connectTimeoutMillis = 15_000
            // 流式场景 socket 超时约束的是「相邻数据块间隔」，不是总时长
            socketTimeoutMillis = 60_000
        }
    }

    /**
     * 请求解读，增量经 [onDelta] 回调，返回全文。
     *
     * @throws DigestException 任何失败，携带分类后的 [DigestError]；
     *   中途断流按可重试处理，已渲染的部分由调用方丢弃
     */
    open suspend fun stream(
        source: String,
        externalId: String,
        onDelta: (String) -> Unit = {},
    ): DigestResult {
        val lang = globalSettingsManager.currentContentLang()
        try {
            return client.preparePost("$baseUrl/detail-summary") {
                header("X-Install-Id", globalSettingsManager.getOrCreateInstallId())
                header("User-Agent", getUserAgent())
                // 已登录才带 token：生成仅登录档开放（服务端真闸），匿名只能命中缓存
                globalAuthManager.getAccessToken()?.let { header("Authorization", "Bearer $it") }
                contentType(ContentType.Application.Json)
                setBody(DigestRequest(source = source, externalId = externalId, lang = lang))
            }.execute { response ->
                if (response.status.value !in 200..299) {
                    throw DigestException(classify(response.status.value, response.bodyAsText()))
                }
                val channel = response.bodyAsChannel()
                val full = StringBuilder()
                var cached: Boolean? = null
                while (cached == null) {
                    val line = channel.readUTF8Line() ?: break
                    when (val event = DigestSse.parseLine(line)) {
                        is DigestSse.Event.Delta -> {
                            full.append(event.text)
                            onDelta(event.text)
                        }
                        is DigestSse.Event.Done -> cached = event.cached
                        null -> Unit
                    }
                }
                // 收到 done 之前流就结束 = 中途断流，服务端未落缓存也未计费，可重试
                val hitCache = cached
                    ?: throw DigestException(DigestError.Retryable("stream ended before done"))
                DigestResult(full.toString(), hitCache)
            }
        } catch (e: DigestException) {
            throw e
        } catch (e: CancellationException) {
            throw e // 不吞协程取消
        } catch (e: Exception) {
            throw DigestException(DigestError.Retryable(e.message))
        }
    }

    /** 非 2xx：按状态码 + 服务端机器码分类 */
    private fun classify(status: Int, body: String): DigestError =
        classifyDigestError(status, runCatching { json.decodeFromString<ErrorResponse>(body) }.getOrNull()?.code)
}

/**
 * 状态码 + 服务端机器码 → 错误分类（纯函数，便于单测）。
 * 机器码缺失时只按状态码判：老服务端或代理改写响应体都不该让分类崩掉。
 */
internal fun classifyDigestError(status: Int, code: String?): DigestError = when {
    status == 403 && code == "login_required" -> DigestError.LoginRequired
    status == 429 -> DigestError.Quota(global = code == "quota_global")
    status == 400 && code == "no_content" -> DigestError.NoContent
    status == 404 -> DigestError.NotFound
    else -> DigestError.Retryable(code ?: "HTTP $status")
}

/**
 * SSE 行解析（纯函数，便于单测）。协议与 Worker 端 `lib/sse.js` 一一对应：
 * `data: {"delta":"..."}` 增量、`data: {"done":true,"cached":bool}` 收尾。
 */
internal object DigestSse {

    sealed interface Event {
        data class Delta(val text: String) : Event
        data class Done(val cached: Boolean) : Event
    }

    private val json = Json { ignoreUnknownKeys = true }

    /** 无法识别的行一律返回 null（容忍 keep-alive 注释、脏行与服务端新增事件） */
    fun parseLine(line: String): Event? {
        val trimmed = line.trim()
        if (!trimmed.startsWith("data:")) return null
        val payload = trimmed.removePrefix("data:").trim()
        if (payload.isEmpty()) return null
        val obj = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return null
        (obj["delta"] as? JsonPrimitive)?.contentOrNull?.let { return Event.Delta(it) }
        if ((obj["done"] as? JsonPrimitive)?.booleanOrNull == true) {
            return Event.Done(cached = (obj["cached"] as? JsonPrimitive)?.booleanOrNull == true)
        }
        return null
    }
}
