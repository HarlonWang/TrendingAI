package whl.trending.ai.ui.home

import kotlin.test.Test
import kotlin.test.assertEquals

class HomeTabTest {

    @Test
    fun fromNameOrDefault_resolves_every_valid_name() {
        HomeTab.entries.forEach { tab ->
            assertEquals(tab, HomeTab.fromNameOrDefault(tab.name))
        }
    }

    @Test
    fun fromNameOrDefault_falls_back_to_github_on_unknown_name() {
        assertEquals(HomeTab.GitHub, HomeTab.fromNameOrDefault("NoSuchTab"))
    }

    @Test
    fun fromNameOrDefault_falls_back_to_github_on_blank() {
        assertEquals(HomeTab.GitHub, HomeTab.fromNameOrDefault(""))
    }

    @Test
    fun fromNameOrDefault_is_case_sensitive_like_storage() {
        // 存储值就是 HomeTab.name 原文，大小写不符视为非法、回落 GitHub
        assertEquals(HomeTab.GitHub, HomeTab.fromNameOrDefault("picks"))
    }
}
