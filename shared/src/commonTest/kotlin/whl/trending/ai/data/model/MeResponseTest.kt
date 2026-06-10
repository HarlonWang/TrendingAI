package whl.trending.ai.data.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MeResponseTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun decode_full_payload() {
        val payload = """
            {"user":{"user_id":"uuid-1","github_user_id":123456,"github_login":"HarlonWang",
            "display_name":"Harlon","avatar_url":"https://a.png","bio":"dev",
            "html_url":"https://github.com/HarlonWang","created_at":"2026-06-10 00:00:00"}}
        """.trimIndent()
        val me = json.decodeFromString<MeResponse>(payload)
        assertEquals("uuid-1", me.user.userId)
        assertEquals(123456L, me.user.githubUserId)
        assertEquals("HarlonWang", me.user.githubLogin)
        assertEquals("https://a.png", me.user.avatarUrl)
    }

    @Test
    fun decode_minimal_payload_with_nulls() {
        val payload = """{"user":{"user_id":"uuid-2"}}"""
        val me = json.decodeFromString<MeResponse>(payload)
        assertEquals("uuid-2", me.user.userId)
        assertNull(me.user.githubLogin)
        assertNull(me.user.avatarUrl)
    }
}
