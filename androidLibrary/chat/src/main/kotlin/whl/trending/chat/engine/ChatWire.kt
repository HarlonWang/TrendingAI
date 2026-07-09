package whl.trending.chat.engine

import kotlin.io.encoding.Base64
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import whl.trending.chat.model.ChatMessage
import whl.trending.chat.model.Role

/**
 * OpenAI 兼容 wire 格式的 content 组装（纯函数，便于单测）。
 *
 * 规则（与服务端校验一一对应）：
 * - 无图消息：content 是普通 JSON string，与历史行为完全一致
 * - **仅末条 user 消息**内嵌图片：content 为 `[{type:text},{type:image_url,...}]` parts 数组，
 *   图片读文件转 `data:image/jpeg;base64,` data-url
 * - 历史里的带图消息降级为「文本 + 占位」纯文本，不重传 base64（防上下文体积膨胀，
 *   也是服务端 images_in_history 校验的前提）
 */
internal object ChatWire {

    /** 单条消息图片数上限，与服务端 MAX_IMAGES_PER_MSG 对齐 */
    const val MAX_IMAGES_PER_MESSAGE = 4

    fun buildContent(
        message: ChatMessage,
        isLast: Boolean,
        imagePlaceholder: String,
        readImageBytes: (String) -> ByteArray?,
    ): JsonElement {
        if (message.images.isEmpty()) return JsonPrimitive(message.content)

        if (!isLast || message.role != Role.USER) {
            val placeholders = imagePlaceholder.repeat(message.images.size)
            val text = if (message.content.isBlank()) placeholders else "${message.content} $placeholders"
            return JsonPrimitive(text)
        }

        return buildJsonArray {
            if (message.content.isNotBlank()) {
                add(
                    buildJsonObject {
                        put("type", "text")
                        put("text", message.content)
                    },
                )
            }
            var encoded = 0
            for (path in message.images.take(MAX_IMAGES_PER_MESSAGE)) {
                val bytes = readImageBytes(path) ?: continue
                add(
                    buildJsonObject {
                        put("type", "image_url")
                        putJsonObject("image_url") {
                            put("url", "data:image/jpeg;base64," + Base64.encode(bytes))
                        }
                    },
                )
                encoded++
            }
            // 图片全部读取失败且无文本：兜底一个占位文本部分，保证 content 合法非空
            if (encoded == 0 && message.content.isBlank()) {
                add(
                    buildJsonObject {
                        put("type", "text")
                        put("text", imagePlaceholder)
                    },
                )
            }
        }
    }
}
