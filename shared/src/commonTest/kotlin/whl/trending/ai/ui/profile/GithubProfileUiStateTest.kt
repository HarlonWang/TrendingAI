package whl.trending.ai.ui.profile

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GithubProfileUiStateTest {

    private val oneItem = listOf(
        GithubFeedItem(
            id = "1",
            kind = GithubFeedKind.OTHER,
            actorLogin = "a",
            actorAvatarUrl = null,
            repoName = "a/b",
            primary = null,
            targetUrl = "https://x",
            createdAt = "2026-06-13T00:00:00Z",
        )
    )

    @Test
    fun pending_first_load_shows_loading() {
        // 头部已出、token/档案等前置请求进行中：feed 空且未到底/未不可用 → 应显示 loading
        val state = GithubProfileUiState(isFeedLoading = false)
        assertTrue(state.isFeedLoadingVisible)
    }

    @Test
    fun actively_fetching_shows_loading() {
        val state = GithubProfileUiState(isFeedLoading = true)
        assertTrue(state.isFeedLoadingVisible)
    }

    @Test
    fun unavailable_does_not_show_loading() {
        val state = GithubProfileUiState(isFeedLoading = false, feedUnavailable = true)
        assertFalse(state.isFeedLoadingVisible)
    }

    @Test
    fun empty_but_end_reached_does_not_show_loading() {
        val state = GithubProfileUiState(isFeedLoading = false, feedEndReached = true)
        assertFalse(state.isFeedLoadingVisible)
    }

    @Test
    fun has_items_and_not_fetching_does_not_show_loading() {
        val state = GithubProfileUiState(isFeedLoading = false, feedItems = oneItem)
        assertFalse(state.isFeedLoadingVisible)
    }

    @Test
    fun has_items_and_fetching_more_shows_loading() {
        val state = GithubProfileUiState(isFeedLoading = true, feedItems = oneItem)
        assertTrue(state.isFeedLoadingVisible)
    }
}
