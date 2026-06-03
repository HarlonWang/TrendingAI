package whl.trending.chat.engine

import whl.trending.chat.model.ChatErrorCategory
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 异常分类器 [ChatErrors] 的纯函数单测：HTTP 状态码 / 传输异常 → [ChatErrorCategory]。 */
class ChatErrorsTest {

    @Test
    fun status_429_is_quota_with_status_and_detail() {
        val e = ChatErrors.forStatus(429, "limit reached")
        assertEquals(ChatErrorCategory.QUOTA, e.category)
        assertEquals(429, e.httpStatus)
        assertEquals("limit reached", e.detail)
    }

    @Test
    fun status_400_is_bad_request() {
        assertEquals(ChatErrorCategory.BAD_REQUEST, ChatErrors.forStatus(400, null).category)
    }

    @Test
    fun status_500_is_server() {
        assertEquals(ChatErrorCategory.SERVER, ChatErrors.forStatus(500, null).category)
    }

    @Test
    fun status_502_is_server() {
        assertEquals(ChatErrorCategory.SERVER, ChatErrors.forStatus(502, null).category)
    }

    @Test
    fun socket_timeout_is_timeout() {
        assertEquals(
            ChatErrorCategory.TIMEOUT,
            ChatErrors.forThrowable(SocketTimeoutException("timeout")).category,
        )
    }

    @Test
    fun unknown_host_is_network() {
        assertEquals(
            ChatErrorCategory.NETWORK,
            ChatErrors.forThrowable(UnknownHostException("no dns")).category,
        )
    }

    @Test
    fun connect_exception_is_network() {
        assertEquals(
            ChatErrorCategory.NETWORK,
            ChatErrors.forThrowable(ConnectException("refused")).category,
        )
    }

    @Test
    fun generic_throwable_is_unknown() {
        assertEquals(
            ChatErrorCategory.UNKNOWN,
            ChatErrors.forThrowable(RuntimeException("boom")).category,
        )
    }

    @Test
    fun quota_and_bad_request_are_not_retryable() {
        assertFalse(ChatErrorCategory.QUOTA.retryable)
        assertFalse(ChatErrorCategory.BAD_REQUEST.retryable)
    }

    @Test
    fun network_timeout_server_unknown_are_retryable() {
        assertTrue(ChatErrorCategory.NETWORK.retryable)
        assertTrue(ChatErrorCategory.TIMEOUT.retryable)
        assertTrue(ChatErrorCategory.SERVER.retryable)
        assertTrue(ChatErrorCategory.UNKNOWN.retryable)
    }
}
