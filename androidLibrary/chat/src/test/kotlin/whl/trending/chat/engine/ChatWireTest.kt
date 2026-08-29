package whl.trending.chat.engine

import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import whl.trending.chat.model.ChatMessage
import whl.trending.chat.model.Role

class ChatWireTest {

    private val bytes = byteArrayOf(1, 2, 3)
    private val reader: (String) -> ByteArray? = { path -> if (path.startsWith("ok")) bytes else null }

    private fun user(id: Long, text: String, images: List<String> = emptyList()) =
        ChatMessage(id, Role.USER, text, images = images)

    @Test
    fun `纯文本消息 content 仍是 JSON string`() {
        val element = ChatWire.buildContent(user(1, "hi"), isLast = true, imagePlaceholder = "[图片]", maxImages = 4, readImageBytes = reader)
        assertEquals(JsonPrimitive("hi"), element)
    }

    @Test
    fun `末条 user 带图转 parts 数组，文本在前图片在后`() {
        val element = ChatWire.buildContent(
            user(1, "这是什么", images = listOf("ok1.jpg", "ok2.jpg")),
            isLast = true,
            imagePlaceholder = "[图片]", maxImages = 4,
            readImageBytes = reader,
        )
        val parts = element.jsonArray
        assertEquals(3, parts.size)
        assertEquals("text", parts[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("这是什么", parts[0].jsonObject["text"]!!.jsonPrimitive.content)
        assertEquals("image_url", parts[1].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals(
            "data:image/jpeg;base64," + Base64.encode(bytes),
            parts[1].jsonObject["image_url"]!!.jsonObject["url"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun `纯图无文本时 parts 里没有 text 部分`() {
        val element = ChatWire.buildContent(
            user(1, "", images = listOf("ok1.jpg")),
            isLast = true,
            imagePlaceholder = "[图片]", maxImages = 4,
            readImageBytes = reader,
        )
        val parts = element.jsonArray
        assertEquals(1, parts.size)
        assertEquals("image_url", parts[0].jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun `历史带图消息降级为文本加占位，不重传 base64`() {
        val element = ChatWire.buildContent(
            user(1, "看这张", images = listOf("ok1.jpg", "ok2.jpg")),
            isLast = false,
            imagePlaceholder = "[图片]", maxImages = 4,
            readImageBytes = reader,
        )
        assertEquals(JsonPrimitive("看这张 [图片][图片]"), element)
    }

    @Test
    fun `历史纯图消息降级为占位文本`() {
        val element = ChatWire.buildContent(
            user(1, "", images = listOf("ok1.jpg")),
            isLast = false,
            imagePlaceholder = "[image]", maxImages = 4,
            readImageBytes = reader,
        )
        assertEquals(JsonPrimitive("[image]"), element)
    }

    @Test
    fun `图片文件读取失败则跳过该张`() {
        val element = ChatWire.buildContent(
            user(1, "hi", images = listOf("missing.jpg", "ok1.jpg")),
            isLast = true,
            imagePlaceholder = "[图片]", maxImages = 4,
            readImageBytes = reader,
        )
        val parts = element.jsonArray
        assertEquals(2, parts.size) // text + 1 张成功的图
    }

    @Test
    fun `全部图片读取失败且无文本时兜底占位文本部分`() {
        val element = ChatWire.buildContent(
            user(1, "", images = listOf("missing.jpg")),
            isLast = true,
            imagePlaceholder = "[图片]", maxImages = 4,
            readImageBytes = reader,
        )
        val parts = element.jsonArray
        assertEquals(1, parts.size)
        assertEquals("text", parts[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("[图片]", parts[0].jsonObject["text"]!!.jsonPrimitive.content)
    }

    @Test
    fun `超过 4 张只取前 4 张`() {
        val element = ChatWire.buildContent(
            user(1, "", images = List(6) { "ok$it.jpg" }),
            isLast = true,
            imagePlaceholder = "[图片]", maxImages = 4,
            readImageBytes = reader,
        )
        assertTrue(element is JsonArray)
        assertEquals(4, element.jsonArray.size)
    }
}
