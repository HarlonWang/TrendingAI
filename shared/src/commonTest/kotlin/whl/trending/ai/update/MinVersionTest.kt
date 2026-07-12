package whl.trending.ai.update

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MinVersionTest {

    // ---- 基本比较 ----

    @Test
    fun lower_version_is_blocked() {
        assertTrue(isVersionBlocked(current = "0.14.0", minVersion = "0.15.0"))
    }

    @Test
    fun equal_version_is_not_blocked() {
        assertFalse(isVersionBlocked(current = "0.15.0", minVersion = "0.15.0"))
    }

    @Test
    fun higher_version_is_not_blocked() {
        assertFalse(isVersionBlocked(current = "0.16.0", minVersion = "0.15.0"))
    }

    @Test
    fun major_bump_compares_numerically_not_lexically() {
        // 数值比较：0.10.0 > 0.9.0；字典序会误判
        assertFalse(isVersionBlocked(current = "0.10.0", minVersion = "0.9.0"))
        assertTrue(isVersionBlocked(current = "0.9.0", minVersion = "0.10.0"))
        assertTrue(isVersionBlocked(current = "1.9.9", minVersion = "2.0.0"))
    }

    // ---- 缺段补 0 ----

    @Test
    fun missing_segments_are_treated_as_zero() {
        assertFalse(isVersionBlocked(current = "0.15", minVersion = "0.15.0"))
        assertTrue(isVersionBlocked(current = "0.15", minVersion = "0.15.1"))
        assertFalse(isVersionBlocked(current = "0.15.1", minVersion = "0.15"))
    }

    // ---- 后缀（prerelease / 本地 git describe）----

    @Test
    fun prerelease_suffix_is_ignored_in_comparison() {
        assertTrue(isVersionBlocked(current = "0.14.0-beta", minVersion = "0.15.0"))
        assertFalse(isVersionBlocked(current = "0.15.0-beta", minVersion = "0.15.0"))
    }

    @Test
    fun git_describe_suffix_is_ignored() {
        // 本地非 tag 构建的 versionName 形如 0.14.0-5-gabc123
        assertTrue(isVersionBlocked(current = "0.14.0-5-gabc123", minVersion = "0.15.0"))
        assertFalse(isVersionBlocked(current = "0.15.0-5-gabc123", minVersion = "0.15.0"))
    }

    // ---- 防御：解析失败一律不拦截 ----

    @Test
    fun null_or_blank_min_version_is_not_blocked() {
        assertFalse(isVersionBlocked(current = "0.14.0", minVersion = null))
        assertFalse(isVersionBlocked(current = "0.14.0", minVersion = ""))
        assertFalse(isVersionBlocked(current = "0.14.0", minVersion = "  "))
    }

    @Test
    fun malformed_min_version_is_not_blocked() {
        assertFalse(isVersionBlocked(current = "0.14.0", minVersion = "latest"))
        assertFalse(isVersionBlocked(current = "0.14.0", minVersion = "v0.15.0"))
        assertFalse(isVersionBlocked(current = "0.14.0", minVersion = "0..15"))
    }

    @Test
    fun malformed_current_version_is_not_blocked() {
        assertFalse(isVersionBlocked(current = "", minVersion = "0.15.0"))
        assertFalse(isVersionBlocked(current = "unknown", minVersion = "0.15.0"))
    }
}
