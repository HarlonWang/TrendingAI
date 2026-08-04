package whl.trending.ai.ui.home

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HomeTabRequestTest {

    @AfterTest
    fun tearDown() {
        HomeTabRequest.consume()
    }

    @Test
    fun requestHoldsPendingTabUntilConsumed() {
        assertNull(HomeTabRequest.pending.value)

        HomeTabRequest.request(HomeTab.Picks)
        assertEquals(HomeTab.Picks, HomeTabRequest.pending.value)

        HomeTabRequest.consume()
        assertNull(HomeTabRequest.pending.value)
    }

    @Test
    fun latestRequestWins() {
        HomeTabRequest.request(HomeTab.Home)
        HomeTabRequest.request(HomeTab.Picks)
        assertEquals(HomeTab.Picks, HomeTabRequest.pending.value)
    }
}
