package whl.trending.ai.ui.profile

import kotlinx.serialization.json.Json
import whl.trending.ai.auth.FollowingInfo
import whl.trending.ai.data.remote.GithubEventActor
import whl.trending.ai.data.remote.GithubEventDto
import whl.trending.ai.data.remote.GithubEventRepo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    @Test
    fun pull_request_merged_action() {
        val item = event(
            "PullRequestEvent",
            """{"action":"merged","number":15,"pull_request":{"html_url":"https://github.com/owner/repo/pull/15"}}"""
        ).toFeedItem()
        assertEquals(GithubFeedKind.PR_MERGED, item.kind)
        assertEquals("15", item.primary)
        assertEquals("https://github.com/owner/repo/pull/15", item.targetUrl)
    }

    @Test
    fun pull_request_labeled_falls_to_other() {
        val item = event(
            "PullRequestEvent",
            """{"action":"labeled","number":16,"pull_request":{"html_url":"https://github.com/owner/repo/pull/16"}}"""
        ).toFeedItem()
        assertEquals(GithubFeedKind.OTHER, item.kind)
    }

    @Test
    fun highlight_kinds_membership() {
        assertEquals(
            setOf(
                GithubFeedKind.STARRED,
                GithubFeedKind.FORKED,
                GithubFeedKind.RELEASED,
                GithubFeedKind.CREATED_REPO,
                GithubFeedKind.MADE_PUBLIC,
                GithubFeedKind.PR_OPENED,
                GithubFeedKind.PR_MERGED,
            ),
            HighlightFeedKinds
        )
    }

    // isHighlight 判定测试

    private fun feedItem(
        actorLogin: String,
        repoOwner: String,
        kind: GithubFeedKind,
    ) = GithubFeedItem(
        id = "1",
        actorLogin = actorLogin,
        actorAvatarUrl = null,
        repoName = "$repoOwner/repo",
        kind = kind,
        primary = null,
        createdAt = "2026-06-09T12:47:28Z",
        targetUrl = "https://github.com/$repoOwner/repo",
    )

    @Test
    fun isHighlight_rule1_followed_user_with_highlight_kind() {
        val following = FollowingInfo(users = setOf("octocat"), orgs = emptySet())
        val item = feedItem("octocat", "someone", GithubFeedKind.STARRED)
        assertTrue(item.isHighlight(following))
    }

    @Test
    fun isHighlight_rule1_unknown_actor_not_highlight() {
        val following = FollowingInfo(users = setOf("octocat"), orgs = emptySet())
        val item = feedItem("stranger", "someone", GithubFeedKind.STARRED)
        assertFalse(item.isHighlight(following))
    }

    @Test
    fun isHighlight_rule2_org_release_is_highlight() {
        val following = FollowingInfo(users = emptySet(), orgs = setOf("myorg"))
        // actor is a random person, but repo owner is the org
        val item = feedItem("randomuser", "myorg", GithubFeedKind.RELEASED)
        assertTrue(item.isHighlight(following))
    }

    @Test
    fun isHighlight_rule2_org_star_by_stranger_not_highlight() {
        val following = FollowingInfo(users = emptySet(), orgs = setOf("myorg"))
        val item = feedItem("randomuser", "myorg", GithubFeedKind.STARRED)
        assertFalse(item.isHighlight(following))
    }

    @Test
    fun isHighlight_null_following_fallback_to_kind_plus_not_bot() {
        val humanItem = feedItem("octocat", "someone", GithubFeedKind.STARRED)
        assertTrue(humanItem.isHighlight(null))

        val botItem = feedItem("cursor[bot]", "someone", GithubFeedKind.STARRED)
        assertFalse(botItem.isHighlight(null))

        val lowSignalItem = feedItem("octocat", "someone", GithubFeedKind.PUSHED)
        assertFalse(lowSignalItem.isHighlight(null))
    }

    @Test
    fun isHighlight_rule1_bot_filtered_even_if_followed() {
        val following = FollowingInfo(users = setOf("cursor[bot]"), orgs = emptySet())
        val item = feedItem("cursor[bot]", "someone", GithubFeedKind.STARRED)
        assertFalse(item.isHighlight(following))
    }

    @Test
    fun isHighlight_rule2_bot_actor_not_filtered_for_org() {
        // CI bot releasing for org should be included
        val following = FollowingInfo(users = emptySet(), orgs = setOf("myorg"))
        val item = feedItem("ci-bot[bot]", "myorg", GithubFeedKind.RELEASED)
        assertTrue(item.isHighlight(following))
    }

    @Test
    fun isHighlight_case_insensitive_login_comparison() {
        val following = FollowingInfo(users = setOf("octocat"), orgs = setOf("myorg"))
        // actor login in mixed case
        val userItem = feedItem("OctoCat", "someone", GithubFeedKind.STARRED)
        assertTrue(userItem.isHighlight(following))

        val orgItem = feedItem("randomer", "MyOrg", GithubFeedKind.RELEASED)
        assertTrue(orgItem.isHighlight(following))
    }

    @Test
    fun bot_actor_detected() {
        val botEvent = GithubEventDto(
            id = "2",
            type = "WatchEvent",
            actor = GithubEventActor(login = "cursor[bot]", avatarUrl = null),
            repo = GithubEventRepo(name = "owner/repo"),
            payload = null,
            createdAt = "2026-06-09T12:47:28Z",
        )
        val humanEvent = GithubEventDto(
            id = "3",
            type = "WatchEvent",
            actor = GithubEventActor(login = "octocat", avatarUrl = null),
            repo = GithubEventRepo(name = "owner/repo"),
            payload = null,
            createdAt = "2026-06-09T12:47:28Z",
        )
        assertEquals(true, botEvent.toFeedItem().isBot())
        assertEquals(false, humanEvent.toFeedItem().isBot())
    }
}
