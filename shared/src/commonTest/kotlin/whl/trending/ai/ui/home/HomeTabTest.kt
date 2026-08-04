package whl.trending.ai.ui.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HomeTabTest {

    @Test
    fun fromNameOrDefault_resolves_every_valid_name() {
        HomeTab.entries.forEach { tab ->
            assertEquals(tab, HomeTab.fromNameOrDefault(tab.name))
        }
    }

    @Test
    fun fromNameOrDefault_falls_back_to_trending_on_unknown_name() {
        assertEquals(HomeTab.Trending, HomeTab.fromNameOrDefault("NoSuchTab"))
    }

    @Test
    fun fromNameOrDefault_falls_back_to_trending_on_blank() {
        assertEquals(HomeTab.Trending, HomeTab.fromNameOrDefault(""))
    }

    @Test
    fun fromNameOrDefault_is_case_sensitive_like_storage() {
        // 存储值就是 HomeTab.name 原文，大小写不符视为非法、回落 Trending
        assertEquals(HomeTab.Trending, HomeTab.fromNameOrDefault("picks"))
    }

    @Test
    fun legacy_source_tabs_resolve_to_trending() {
        // 0.23 前底栏的三个源名：它们现在是 Trending 的子源，落到 Trending 才是同一个位置
        listOf("GitHub", "HackerNews", "ProductHunt").forEach { legacy ->
            assertEquals(HomeTab.Trending, HomeTab.fromNameOrDefault(legacy))
        }
    }

    @Test
    fun picks_survives_the_rename() {
        // Picks 名字没变，老用户存的默认首页应当原样保留
        assertEquals(HomeTab.Picks, HomeTab.fromNameOrDefault("Picks"))
    }

    @Test
    fun defaultCandidates_excludes_chat() {
        assertFalse(HomeTab.Chat in HomeTab.defaultCandidates)
        assertEquals(listOf(HomeTab.Trending, HomeTab.Picks, HomeTab.Me), HomeTab.defaultCandidates)
    }

    @Test
    fun defaultFromName_treats_chat_as_invalid() {
        // Chat 只是入口：即便被写进设置也不能当落点
        assertEquals(HomeTab.Trending, HomeTab.defaultFromName(HomeTab.Chat.name))
        assertEquals(HomeTab.Me, HomeTab.defaultFromName(HomeTab.Me.name))
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
