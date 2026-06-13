package whl.trending.ai.ui.profile

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProfileUiStateTest {

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
        // load 已出 header、loadGithubData 前置请求进行中：feed 空且未到底/未不可用 → 应显示 loading
        val state = ProfileUiState(isLoading = false, isFeedLoading = false)
        assertTrue(state.isFeedLoadingVisible)
    }

    @Test
    fun full_page_loading_does_not_show_feed_loading() {
        // 整页 loading 阶段不渲染 feed spinner，避免与整页 loading 叠加
        val state = ProfileUiState(isLoading = true)
        assertFalse(state.isFeedLoadingVisible)
    }

    @Test
    fun actively_fetching_shows_loading() {
        val state = ProfileUiState(isLoading = false, isFeedLoading = true)
        assertTrue(state.isFeedLoadingVisible)
    }

    @Test
    fun unavailable_does_not_show_loading() {
        val state = ProfileUiState(isLoading = false, isFeedLoading = false, feedUnavailable = true)
        assertFalse(state.isFeedLoadingVisible)
    }

    @Test
    fun empty_but_end_reached_does_not_show_loading() {
        val state = ProfileUiState(isLoading = false, isFeedLoading = false, feedEndReached = true)
        assertFalse(state.isFeedLoadingVisible)
    }

    @Test
    fun has_items_and_not_fetching_does_not_show_loading() {
        val state = ProfileUiState(isLoading = false, isFeedLoading = false, feedItems = oneItem)
        assertFalse(state.isFeedLoadingVisible)
    }

    @Test
    fun has_items_and_fetching_more_shows_loading() {
        val state = ProfileUiState(isLoading = false, isFeedLoading = true, feedItems = oneItem)
        assertTrue(state.isFeedLoadingVisible)
    }
}
