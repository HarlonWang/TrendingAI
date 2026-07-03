package whl.trending.ai.ui.profile

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import whl.trending.ai.data.local.FakeCacheFileStore
import whl.trending.ai.data.local.LastDataCache
import whl.trending.ai.data.model.ContributionCalendar
import whl.trending.ai.data.model.ContributionDay
import whl.trending.ai.data.model.ContributionLevel
import whl.trending.ai.data.model.ContributionWeek
import whl.trending.ai.data.model.MeUser
import whl.trending.ai.data.remote.GithubUser

fun profileFeedItem(id: String) = GithubFeedItem(
    id = id,
    actorLogin = "actor",
    actorAvatarUrl = null,
    repoName = "octo/repo",
    kind = GithubFeedKind.STARRED,
    primary = null,
    createdAt = "2026-07-01T00:00:00Z",
    targetUrl = "https://github.com/octo/repo",
)

fun profileCalendar(total: Int = 42) = ContributionCalendar(
    total = total,
    weeks = listOf(
        ContributionWeek(
            days = listOf(ContributionDay(date = "2026-07-01", weekday = 3, count = 5, level = ContributionLevel.SECOND)),
        ),
    ),
)

fun profileMeUser(login: String? = "octo") = MeUser(userId = "u1", githubLogin = login, displayName = "Octo")

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileCacheTest {

    private fun state(
        user: MeUser? = profileMeUser(),
        githubUser: GithubUser? = GithubUser(login = "octo", followers = 10),
        contributions: ContributionCalendar? = profileCalendar(),
        feedItems: List<GithubFeedItem> = listOf(profileFeedItem("e1")),
        highlightsOnly: Boolean = true,
    ) = ProfileUiState(
        user = user,
        githubUser = githubUser,
        contributions = contributions,
        feedItems = feedItems,
        highlightsOnly = highlightsOnly,
    )

    @Test
    fun fromReturnsNullWithoutUserOrLogin() {
        assertNull(ProfileCache.from(state(user = null), previous = null))
        assertNull(ProfileCache.from(state(user = profileMeUser(login = null)), previous = null))
    }

    @Test
    fun fromCapsFeedItemsAtLimit() {
        val many = (1..80).map { profileFeedItem("e$it") }
        val snapshot = ProfileCache.from(state(feedItems = many), previous = null)!!
        assertEquals(ProfileCache.MAX_FEED_ITEMS, snapshot.feedItems.size)
        assertEquals("e1", snapshot.feedItems.first().id)
    }

    @Test
    fun fromKeepsPreviousFieldsWhenCurrentMissing() {
        // 只增不减：刷新中途 contributions/feed 尚未到达时，用旧快照补齐再覆盖
        val previous = ProfileCache(
            login = "octo",
            user = profileMeUser(),
            githubUser = GithubUser(login = "octo", followers = 3),
            contributions = profileCalendar(total = 99),
            feedItems = listOf(profileFeedItem("old")),
            highlightsOnly = true,
        )
        val snapshot = ProfileCache.from(
            state(githubUser = null, contributions = null, feedItems = emptyList()),
            previous,
        )!!
        assertEquals(99, snapshot.contributions?.total)
        assertEquals(listOf("old"), snapshot.feedItems.map { it.id })
        assertEquals(3, snapshot.githubUser?.followers)
    }

    @Test
    fun fromDropsPreviousFeedWhenFilterDiffers() {
        // 档位不一致时旧 feed 不可复用，但档位无关的 contributions 仍补齐
        val previous = ProfileCache(
            login = "octo",
            user = profileMeUser(),
            githubUser = null,
            contributions = profileCalendar(total = 99),
            feedItems = listOf(profileFeedItem("old")),
            highlightsOnly = true,
        )
        val snapshot = ProfileCache.from(
            state(contributions = null, feedItems = emptyList(), highlightsOnly = false),
            previous,
        )!!
        assertEquals(emptyList(), snapshot.feedItems)
        assertEquals(99, snapshot.contributions?.total)
        assertEquals(false, snapshot.highlightsOnly)
    }

    @Test
    fun fromIgnoresPreviousOfDifferentLogin() {
        val previous = ProfileCache(
            login = "someone-else",
            user = profileMeUser(login = "someone-else"),
            githubUser = null,
            contributions = profileCalendar(total = 99),
            feedItems = listOf(profileFeedItem("old")),
            highlightsOnly = true,
        )
        val snapshot = ProfileCache.from(
            state(contributions = null, feedItems = emptyList()),
            previous,
        )!!
        assertNull(snapshot.contributions)
        assertEquals(emptyList(), snapshot.feedItems)
    }

    @Test
    fun roundTripsThroughLastDataCache() = runTest {
        val cache = LastDataCache(FakeCacheFileStore(), StandardTestDispatcher(testScheduler))
        val snapshot = ProfileCache.from(state(), previous = null)!!
        cache.put(ProfileCache.KEY, snapshot)
        assertEquals(snapshot, cache.get(ProfileCache.KEY))
    }
}
