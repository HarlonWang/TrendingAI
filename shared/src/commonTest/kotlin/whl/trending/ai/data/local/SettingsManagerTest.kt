package whl.trending.ai.data.local

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsManagerTest {

    private lateinit var settings: MapSettings
    private lateinit var manager: SettingsManager

    @BeforeTest
    fun setUp() {
        settings = MapSettings()
        manager = SettingsManager(settings)
    }

    @Test
    fun getOrCreateInstallId_returns_non_blank_id() {
        val id = manager.getOrCreateInstallId()
        assertTrue(id.isNotBlank())
    }

    @Test
    fun getOrCreateInstallId_is_idempotent() {
        val first = manager.getOrCreateInstallId()
        val second = manager.getOrCreateInstallId()
        assertEquals(first, second)
    }

    @Test
    fun installId_persists_across_manager_instances() {
        val first = manager.getOrCreateInstallId()
        // 模拟应用重启：同一份底层存储，新建 manager
        val rebuilt = SettingsManager(settings)
        assertEquals(first, rebuilt.getOrCreateInstallId())
    }

    @Test
    fun seedColor_default_is_baseline_purple() = runTest {
        val first = manager.seedColor.first()
        assertEquals(0xFF6750A4L, first)
    }

    @Test
    fun setSeedColor_updates_flow() = runTest {
        manager.setSeedColor(0xFF1976D2L)
        assertEquals(0xFF1976D2L, manager.seedColor.first())
    }

    @Test
    fun lastSeenWhatsNewVersion_defaults_to_null_and_persists() {
        assertNull(manager.getLastSeenWhatsNewVersion())
        manager.setLastSeenWhatsNewVersion("0.9.0")
        assertEquals("0.9.0", manager.getLastSeenWhatsNewVersion())
    }

    @Test
    fun cachedMinVersion_defaults_to_null_and_persists() {
        assertNull(manager.getCachedMinVersion())
        manager.setCachedMinVersion("0.15.0")
        assertEquals("0.15.0", manager.getCachedMinVersion())
    }

    @Test
    fun cachedMinVersion_null_clears_previous_value() {
        manager.setCachedMinVersion("0.15.0")
        // 服务端撤销强更（min_version 返回 null）时要清掉缓存，避免离线一直误拦
        manager.setCachedMinVersion(null)
        assertNull(manager.getCachedMinVersion())
    }

    @Test
    fun seedColor_and_themeMode_are_independent() = runTest {
        manager.setSeedColor(0xFFC2185BL)
        manager.setThemeMode(ThemeMode.DARK)

        assertEquals(0xFFC2185BL, manager.seedColor.first())
        assertEquals(ThemeMode.DARK, manager.themeMode.first())

        manager.setThemeMode(ThemeMode.LIGHT)
        assertEquals(0xFFC2185BL, manager.seedColor.first())
        assertEquals(ThemeMode.LIGHT, manager.themeMode.first())
    }

    @Test
    fun userAvatarUrl_set_and_clear() {
        manager.setUserAvatarUrl("https://a.png")
        assertEquals("https://a.png", settings.getStringOrNull("prefs_user_avatar_url"))
        manager.setUserAvatarUrl(null)
        assertEquals(null, settings.getStringOrNull("prefs_user_avatar_url"))
    }

    @Test
    fun feedHighlightsOnly_defaults_true_and_persists() = runTest {
        // 默认值
        assertEquals(true, manager.currentFeedHighlightsOnly())
        assertEquals(true, manager.feedHighlightsOnly.first())

        // 改为 false
        manager.setFeedHighlightsOnly(false)
        assertEquals(false, manager.currentFeedHighlightsOnly())
        assertEquals(false, manager.feedHighlightsOnly.first())

        // 改回 true
        manager.setFeedHighlightsOnly(true)
        assertEquals(true, manager.currentFeedHighlightsOnly())
    }

    @Test
    fun openLinksInCustomTab_defaults_true_and_persists() = runTest {
        // 默认值：外链走系统浏览器（Custom Tabs）
        assertEquals(true, manager.currentOpenLinksInCustomTab())
        assertEquals(true, manager.openLinksInCustomTab.first())

        // 切到内置 WebView
        manager.setOpenLinksInCustomTab(false)
        assertEquals(false, manager.currentOpenLinksInCustomTab())
        assertEquals(false, manager.openLinksInCustomTab.first())

        // 切回系统浏览器
        manager.setOpenLinksInCustomTab(true)
        assertEquals(true, manager.currentOpenLinksInCustomTab())
    }

    @Test
    fun defaultHomeTab_defaults_to_home_and_persists() = runTest {
        // 默认值：Home
        assertEquals("Home", manager.currentDefaultHomeTab())
        assertEquals("Home", manager.defaultHomeTab.first())

        // 改为 Picks
        manager.setDefaultHomeTab("Picks")
        assertEquals("Picks", manager.currentDefaultHomeTab())
        assertEquals("Picks", manager.defaultHomeTab.first())

        // 模拟应用重启：同一份底层存储，新建 manager
        val rebuilt = SettingsManager(settings)
        assertEquals("Picks", rebuilt.currentDefaultHomeTab())
    }

    @Test
    fun trendingSource_defaults_to_github_and_persists() = runTest {
        // 默认值：GitHub
        assertEquals("GitHub", manager.currentTrendingSource())

        // 切到 HN
        manager.setTrendingSource("HackerNews")
        assertEquals("HackerNews", manager.currentTrendingSource())

        // 模拟应用重启：上次看的源要能带回来
        val rebuilt = SettingsManager(settings)
        assertEquals("HackerNews", rebuilt.currentTrendingSource())
    }

    @Test
    fun immersiveBrowsing_defaults_off_and_persists() = runTest {
        // 默认关：常驻顶/底栏是导航锚点，沉浸式留给需要的人自己打开
        assertEquals(false, manager.currentImmersiveBrowsing())

        manager.setImmersiveBrowsing(true)
        assertEquals(true, manager.currentImmersiveBrowsing())

        // 模拟应用重启：开关状态要能带回来
        val rebuilt = SettingsManager(settings)
        assertEquals(true, rebuilt.currentImmersiveBrowsing())
    }

    @Test
    fun accountLinkPending_persists_across_process_restart() = runTest {
        assertEquals(false, manager.accountLinkPending())

        manager.setAccountLinkPending(true)

        // 绑定要跳出去开系统浏览器，授权期间进程随时可能被系统回收，回跳时是冷启动。
        // 标记若只在内存里，那时已经没了——绑定失败的 ?error= 会被误判成登录失败，
        // 提示分派到错误的地方（登录面板而非账户页）
        val rebuilt = SettingsManager(settings)
        assertEquals(true, rebuilt.accountLinkPending())
    }
}
