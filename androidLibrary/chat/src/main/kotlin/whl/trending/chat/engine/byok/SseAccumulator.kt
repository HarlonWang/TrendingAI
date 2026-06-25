package whl.trending.chat.engine.byok

/** 一个已分发的 SSE 事件。[event] 为命名事件类型（Anthropic 用），OpenAI 多为 null。 */
data class ServerSentEvent(
    val event: String?,
    val data: String,
)

/**
 * SSE 流增量帧解析器（纯同步、无协程）。
 *
 * 反复 [feed] 任意大小的文本块，返回这次喂入后已完整的事件列表；
 * 内部缓冲跨块的半行，按 W3C SSE 规则在空行处分发事件：
 * - `\r\n` / `\r` / `\n` 任一行尾均可
 * - 冒号开头的行是注释，忽略
 * - `field: value`（冒号后可选一个空格）；多条 `data:` 以 `\n` 拼接
 * - 仅当遇空行且当前块出现过字段时才分发（避免空事件）
 *
 * `[DONE]` 等哨兵原样作为 data 透传，交由上层 provider 适配器处理。
 */
class SseAccumulator {

    private val lineBuffer = StringBuilder()   // 跨 feed 的未完成行
    private val dataBuffer = StringBuilder()    // 当前事件累积的 data（多行）
    private var eventType: String? = null
    private var hasField = false                 // 当前事件块是否出现过字段

    fun feed(chunk: String): List<ServerSentEvent> {
        val out = mutableListOf<ServerSentEvent>()
        lineBuffer.append(chunk)

        var newlineIdx = lineBuffer.indexOf("\n")
        while (newlineIdx >= 0) {
            var line = lineBuffer.substring(0, newlineIdx)
            if (line.endsWith("\r")) line = line.substring(0, line.length - 1)
            lineBuffer.delete(0, newlineIdx + 1)

            processLine(line)?.let { out += it }

            newlineIdx = lineBuffer.indexOf("\n")
        }
        return out
    }

    /** 处理一整行；若该行是分发边界（空行）则返回事件。 */
    private fun processLine(line: String): ServerSentEvent? {
        if (line.isEmpty()) {
            if (!hasField) return null
            val event = ServerSentEvent(eventType, dataBuffer.toString())
            dataBuffer.setLength(0)
            eventType = null
            hasField = false
            return event
        }
        if (line.startsWith(":")) return null // 注释

        val colon = line.indexOf(":")
        val field: String
        val value: String
        if (colon < 0) {
            field = line
            value = ""
        } else {
            field = line.substring(0, colon)
            value = line.substring(colon + 1).removePrefix(" ")
        }

        when (field) {
            "data" -> {
                if (dataBuffer.isNotEmpty()) dataBuffer.append("\n")
                dataBuffer.append(value)
                hasField = true
            }
            "event" -> {
                eventType = value
                hasField = true
            }
            // id / retry 等字段当前不需要，忽略但仍记为出现过字段
            else -> hasField = true
        }
        return null
    }
}
