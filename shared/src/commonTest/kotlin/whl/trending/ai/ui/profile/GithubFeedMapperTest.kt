package whl.trending.ai.ui.profile

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import whl.trending.ai.data.remote.GithubEventActor
import whl.trending.ai.data.remote.GithubEventDto
import whl.trending.ai.data.remote.GithubEventRepo
import kotlin.test.Test
import kotlin.test.assertEquals

class GithubFeedMapperTest {
    private fun event(type: String, payloadJson: String?): GithubEventDto = GithubEventDto(
        id = "1",
        type = type,
        actor = GithubEventActor(login = "octocat", avatarUrl = "https://a.png"),
        repo = GithubEventRepo(name = "owner/repo"),
        payload = payloadJson?.let { Json.parseToJsonElement(it) },
        createdAt = "2026-06-09T12:47:28Z",
    )

    @Test
    fun watch_event_maps_to_starred() {
        val item = event("WatchEvent", """{"action":"started"}""").toFeedItem()
        assertEquals(GithubFeedKind.STARRED, item.kind)
        assertEquals("https://github.com/owner/repo", item.targetUrl)
    }

    @Test
    fun fork_event() {
        assertEquals(GithubFeedKind.FORKED, event("ForkEvent", "{}").toFeedItem().kind)
    }

    @Test
    fun create_event_branch_and_repo_and_tag() {
        val branch = event("CreateEvent", """{"ref":"dev","ref_type":"branch"}""").toFeedItem()
        assertEquals(GithubFeedKind.CREATED_BRANCH, branch.kind)
        assertEquals("dev", branch.primary)

        val repo = event("CreateEvent", """{"ref":null,"ref_type":"repository"}""").toFeedItem()
        assertEquals(GithubFeedKind.CREATED_REPO, repo.kind)

        val tag = event("CreateEvent", """{"ref":"v1.0","ref_type":"tag"}""").toFeedItem()
        assertEquals(GithubFeedKind.CREATED_TAG, tag.kind)
        assertEquals("v1.0", tag.primary)
    }

    @Test
    fun release_event_uses_release_url_and_tag() {
        val item = event(
            "ReleaseEvent",
            """{"action":"published","release":{"tag_name":"v2.1","html_url":"https://github.com/owner/repo/releases/tag/v2.1"}}"""
        ).toFeedItem()
        assertEquals(GithubFeedKind.RELEASED, item.kind)
        assertEquals("v2.1", item.primary)
        assertEquals("https://github.com/owner/repo/releases/tag/v2.1", item.targetUrl)
    }

    @Test
    fun push_event_counts_commits() {
        val item = event("PushEvent", """{"size":3,"ref":"refs/heads/main"}""").toFeedItem()
        assertEquals(GithubFeedKind.PUSHED, item.kind)
        assertEquals("3", item.primary)
    }

    @Test
    fun pull_request_opened_merged_closed() {
        val opened = event(
            "PullRequestEvent",
            """{"action":"opened","number":12,"pull_request":{"merged":false,"html_url":"https://github.com/owner/repo/pull/12"}}"""
        ).toFeedItem()
        assertEquals(GithubFeedKind.PR_OPENED, opened.kind)
        assertEquals("12", opened.primary)
        assertEquals("https://github.com/owner/repo/pull/12", opened.targetUrl)

        val merged = event(
            "PullRequestEvent",
            """{"action":"closed","number":13,"pull_request":{"merged":true,"html_url":"https://github.com/owner/repo/pull/13"}}"""
        ).toFeedItem()
        assertEquals(GithubFeedKind.PR_MERGED, merged.kind)

        val closed = event(
            "PullRequestEvent",
            """{"action":"closed","number":14,"pull_request":{"merged":false,"html_url":"https://github.com/owner/repo/pull/14"}}"""
        ).toFeedItem()
        assertEquals(GithubFeedKind.PR_CLOSED, closed.kind)
    }

    @Test
    fun issues_and_comment_events() {
        val opened = event(
            "IssuesEvent",
            """{"action":"opened","issue":{"number":7,"html_url":"https://github.com/owner/repo/issues/7"}}"""
        ).toFeedItem()
        assertEquals(GithubFeedKind.ISSUE_OPENED, opened.kind)
        assertEquals("7", opened.primary)

        val closed = event(
            "IssuesEvent",
            """{"action":"closed","issue":{"number":8,"html_url":"https://github.com/owner/repo/issues/8"}}"""
        ).toFeedItem()
        assertEquals(GithubFeedKind.ISSUE_CLOSED, closed.kind)

        val comment = event(
            "IssueCommentEvent",
            """{"action":"created","issue":{"number":9,"html_url":"https://github.com/owner/repo/issues/9"},"comment":{"html_url":"https://github.com/owner/repo/issues/9#issuecomment-1"}}"""
        ).toFeedItem()
        assertEquals(GithubFeedKind.ISSUE_COMMENTED, comment.kind)
        assertEquals("https://github.com/owner/repo/issues/9#issuecomment-1", comment.targetUrl)
    }

    @Test
    fun public_event_and_unknown_fallback() {
        assertEquals(GithubFeedKind.MADE_PUBLIC, event("PublicEvent", "{}").toFeedItem().kind)

        val other = event("MemberEvent", """{"action":"added"}""").toFeedItem()
        assertEquals(GithubFeedKind.OTHER, other.kind)
        assertEquals("Member", other.primary) // type 去掉 Event 后缀作展示
    }

    @Test
    fun malformed_payload_does_not_crash() {
        val item = event("PullRequestEvent", null).toFeedItem()
        assertEquals(GithubFeedKind.OTHER, item.kind)
        assertEquals("https://github.com/owner/repo", item.targetUrl)
    }
}
