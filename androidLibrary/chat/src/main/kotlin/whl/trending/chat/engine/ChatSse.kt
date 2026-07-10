package whl.trending.chat.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * SSE 行解析（纯函数，便于单测）。事件协议与 Worker 端 `lib/sse.js` 一一对应：
 * `data: {"delta":"..."}` 增量、`data: {"done":true}` 收尾（可带 `cached` 元信息）。
 * 无法识别的行一律返回 null（容忍 keep-alive 注释与脏行）。
 */
internal object ChatSse {

    sealed interface Event {
        data class Delta(val text: String) : Event
        data class Done(val cached: Boolean) : Event
    }

    private val json = Json { ignoreUnknownKeys = true }

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
