package whl.trending.ai.ui.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeTabTest {

    @Test
    fun fromNameOrDefault_resolves_every_valid_name() {
        HomeTab.entries.forEach { tab ->
            assertEquals(tab, HomeTab.fromNameOrDefault(tab.name))
        }
    }

    @Test
    fun fromNameOrDefault_falls_back_to_home_on_unknown_name() {
        assertEquals(HomeTab.Home, HomeTab.fromNameOrDefault("NoSuchTab"))
    }

    @Test
    fun fromNameOrDefault_falls_back_to_home_on_blank() {
        assertEquals(HomeTab.Home, HomeTab.fromNameOrDefault(""))
    }

    @Test
    fun fromNameOrDefault_is_case_sensitive_like_storage() {
        // 存储值就是 HomeTab.name 原文，大小写不符视为非法、回落 Home
        assertEquals(HomeTab.Home, HomeTab.fromNameOrDefault("picks"))
    }

    @Test
    fun legacy_tab_names_resolve_to_home() {
        // GitHub/HackerNews/ProductHunt 是 0.23 前的三个一级 tab（现为首页的子源），
        // Trending 是首页 tab 改名前的旧名——存量值全靠回落收口，落点与改名前一致
        listOf("GitHub", "HackerNews", "ProductHunt", "Trending").forEach { legacy ->
            assertEquals(HomeTab.Home, HomeTab.fromNameOrDefault(legacy))
        }
    }

    @Test
    fun picks_survives_the_rename() {
        // Picks 名字没变，老用户存的默认首页应当原样保留
        assertEquals(HomeTab.Picks, HomeTab.fromNameOrDefault("Picks"))
    }

    @Test
    fun defaultCandidates_includes_chat_in_bar_order() {
        assertEquals(listOf(HomeTab.Home, HomeTab.Picks, HomeTab.Chat, HomeTab.Me), HomeTab.defaultCandidates)
    }

    @Test
    fun defaultFromName_puts_home_under_chat() {
        // Chat 不是 tab：默认首页选它时聊天页压在 Home 之上，底栏选中的仍是 Home
        assertEquals(HomeTab.Home, HomeTab.defaultFromName(HomeTab.Chat.name))
        assertEquals(HomeTab.Me, HomeTab.defaultFromName(HomeTab.Me.name))
    }

    @Test
    fun launchesChat_only_for_stored_chat() {
        assertTrue(HomeTab.launchesChat(HomeTab.Chat.name))
        assertFalse(HomeTab.launchesChat(HomeTab.Home.name))
        assertFalse(HomeTab.launchesChat("chat"))
        assertFalse(HomeTab.launchesChat(""))
    }
}

class TrendingSourceTest {

    @Test
    fun fromNameOrDefault_resolves_every_valid_name() {
        TrendingSource.entries.forEach { source ->
            assertEquals(source, TrendingSource.fromNameOrDefault(source.name))
        }
    }

    @Test
    fun fromNameOrDefault_falls_back_to_github() {
        assertEquals(TrendingSource.GitHub, TrendingSource.fromNameOrDefault("NoSuchSource"))
        assertEquals(TrendingSource.GitHub, TrendingSource.fromNameOrDefault(""))
        assertEquals(TrendingSource.GitHub, TrendingSource.fromNameOrDefault("hackernews"))
    }
}
