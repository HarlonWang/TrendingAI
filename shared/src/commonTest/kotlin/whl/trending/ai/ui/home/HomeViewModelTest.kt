package whl.trending.ai.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.ObservableSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import whl.trending.ai.data.local.SettingsManager
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val events = mutableListOf<Pair<String, Map<String, Any>>>()
    private val vms = mutableListOf<HomeViewModel>()

    private fun settings(): SettingsManager = SettingsManager(MapSettings() as ObservableSettings)

    /** 统一经此创建：记录埋点供断言，并登记实例以便 tearDown 取消其 scope */
    private fun vm(
        handle: SavedStateHandle = SavedStateHandle(),
        settings: SettingsManager = settings(),
    ): HomeViewModel = HomeViewModel(
        savedStateHandle = handle,
        settingsManager = settings,
        track = { name, props -> events += name to props },
    ).also { vms += it }

    private fun eventsNamed(name: String) = events.filter { it.first == name }

    @BeforeTest
    fun setUp() {
        // HomeTabRequest 是进程级单例，清掉上个用例的残留请求
        HomeTabRequest.consume()
    }

    @AfterTest
    fun tearDown() {
        // VM 的深链 collect 挂在进程级单例上，不取消会活到后续用例里抢先消费请求
        vms.forEach { it.viewModelScope.cancel() }
        vms.clear()
        events.clear()
        HomeTabRequest.consume()
        Dispatchers.resetMain()
    }

    @Test
    fun `初始 tab 取设置页的默认首页`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = vm(settings = settings().apply { setDefaultHomeTab("Picks") })
        assertEquals(HomeTab.Picks, vm.selectedTab.value)
    }

    @Test
    fun `默认首页为非法值或 Chat 时回落 Home`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        assertEquals(
            HomeTab.Home,
            vm(settings = settings().apply { setDefaultHomeTab("Trending") }).selectedTab.value,
        )
        assertEquals(
            HomeTab.Home,
            vm(settings = settings().apply { setDefaultHomeTab("Chat") }).selectedTab.value,
        )
    }

    @Test
    fun `SavedStateHandle 里的 tab 优先于默认首页设置`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = vm(
            handle = SavedStateHandle(mapOf("home_selected_tab" to "Me")),
            settings = settings().apply { setDefaultHomeTab("Picks") },
        )
        assertEquals(HomeTab.Me, vm.selectedTab.value)
    }

    @Test
    fun `selectTab 切换并回写 SavedStateHandle - 重复点击不重复记事件`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val handle = SavedStateHandle()
        val vm = vm(handle = handle)

        vm.selectTab(HomeTab.Me)
        assertEquals(HomeTab.Me, vm.selectedTab.value)
        assertEquals("Me", handle.get<String>("home_selected_tab"))
        assertEquals(listOf(mapOf("tab" to "me")), eventsNamed("tab_switch").map { it.second })

        vm.selectTab(HomeTab.Me)
        assertEquals(1, eventsNamed("tab_switch").size)
    }

    @Test
    fun `backToHome 从任意 tab 回 Home 且不记 tab_switch`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = vm(settings = settings().apply { setDefaultHomeTab("Picks") })
        vm.backToHome()
        assertEquals(HomeTab.Home, vm.selectedTab.value)
        assertEquals(0, eventsNamed("tab_switch").size)
    }

    @Test
    fun `初始子源取上次停留的源 - selectSource 切换并回写设置`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val settings = settings().apply { setTrendingSource("HackerNews") }
        val vm = vm(settings = settings)
        assertEquals(TrendingSource.HackerNews, vm.selectedSource.value)

        vm.selectSource(TrendingSource.ProductHunt)
        assertEquals(TrendingSource.ProductHunt, vm.selectedSource.value)
        assertEquals("ProductHunt", settings.currentTrendingSource())
        assertEquals(listOf(mapOf("source" to "producthunt")), eventsNamed("trending_source_switch").map { it.second })

        // 重复选当前源：不回写不记事件
        vm.selectSource(TrendingSource.ProductHunt)
        assertEquals(1, eventsNamed("trending_source_switch").size)
    }

    @Test
    fun `VM 上线前发出的深链请求会被消费且不记 tab_switch`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        HomeTabRequest.request(HomeTab.Picks)
        val vm = vm()
        advanceUntilIdle()
        assertEquals(HomeTab.Picks, vm.selectedTab.value)
        assertNull(HomeTabRequest.pending.value)
        assertEquals(0, eventsNamed("tab_switch").size)
    }

    @Test
    fun `深链请求 Chat 被消费但不切换`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = vm()
        HomeTabRequest.request(HomeTab.Chat)
        advanceUntilIdle()
        assertEquals(HomeTab.Home, vm.selectedTab.value)
        assertNull(HomeTabRequest.pending.value)
    }
}
