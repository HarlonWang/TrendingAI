package whl.trending.ai.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class GithubApiModelsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun decode_github_user() {
        val payload = """
            {"login":"HarlonWang","followers":491,"following":81,"public_repos":24,"name":"Harlon"}
        """.trimIndent()
        val user = json.decodeFromString<GithubUser>(payload)
        assertEquals("HarlonWang", user.login)
        assertEquals(491, user.followers)
        assertEquals(81, user.following)
        assertEquals(24, user.publicRepos)
    }

    @Test
    fun decode_event_with_payload_kept_raw() {
        val payload = """
            [{"id":"22249084947","type":"WatchEvent",
              "actor":{"id":1,"login":"octocat","avatar_url":"https://a.png"},
              "repo":{"id":2,"name":"octocat/Hello-World"},
              "payload":{"action":"started"},
              "public":true,"created_at":"2026-06-09T12:47:28Z"}]
        """.trimIndent()
        val events = json.decodeFromString<List<GithubEventDto>>(payload)
        assertEquals(1, events.size)
        assertEquals("WatchEvent", events[0].type)
        assertEquals("octocat", events[0].actor.login)
        assertEquals("octocat/Hello-World", events[0].repo.name)
        assertEquals("started", events[0].payload?.jsonObject?.get("action")?.jsonPrimitive?.content)
    }
}
