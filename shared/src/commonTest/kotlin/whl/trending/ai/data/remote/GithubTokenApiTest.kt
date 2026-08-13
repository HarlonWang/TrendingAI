package whl.trending.ai.data.remote

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class GithubTokenApiTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun decode_github_token_response() {
        val payload = """{"access_token":"gho_abc123","scope":"read:user","token_type":"bearer"}"""
        val resp = json.decodeFromString<GithubTokenResponse>(payload)
        assertEquals("gho_abc123", resp.accessToken)
    }

    @Test
    fun decode_minimal_response() {
        val payload = """{"access_token":"gho_x"}"""
        val resp = json.decodeFromString<GithubTokenResponse>(payload)
        assertEquals("gho_x", resp.accessToken)
    }
}
