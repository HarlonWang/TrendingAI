package whl.trending.ai.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class GithubApiModelsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun decode_github_following_user() {
        val payload = """[{"login":"octocat","type":"User","avatar_url":"https://a.png","id":1}]"""
        val list = json.decodeFromString<List<GithubFollowing>>(payload)
        assertEquals(1, list.size)
        assertEquals("octocat", list[0].login)
        assertEquals("User", list[0].type)
    }

    @Test
    fun decode_github_following_organization() {
        val payload = """[{"login":"myorg","type":"Organization","id":99}]"""
        val list = json.decodeFromString<List<GithubFollowing>>(payload)
        assertEquals(1, list.size)
        assertEquals("myorg", list[0].login)
        assertEquals("Organization", list[0].type)
    }

    @Test
    fun decode_github_following_default_type_is_user() {
        // type フィールドが欠落している場合は "User" になる
        val payload = """[{"login":"someone"}]"""
        val list = json.decodeFromString<List<GithubFollowing>>(payload)
        assertEquals("User", list[0].type)
    }

    @Test
    fun decode_own_repos_list() {
        val payload = """
            [
              {"id":1,"full_name":"HarlonWang/repo-a","private":false,"pushed_at":"2026-06-01T00:00:00Z"},
              {"id":2,"full_name":"HarlonWang/repo-b","private":true,"pushed_at":"2026-05-01T00:00:00Z","extra_field":"ignored"}
            ]
        """.trimIndent()
        // 只要能解析出 full_name 列表（多余字段忽略）
        val repos = json.decodeFromString<List<kotlinx.serialization.json.JsonObject>>(payload)
        val names = repos.map {
            it["full_name"]?.let { e -> runCatching { e.jsonPrimitive.content }.getOrNull() }
        }
        assertEquals(listOf("HarlonWang/repo-a", "HarlonWang/repo-b"), names)
    }

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
