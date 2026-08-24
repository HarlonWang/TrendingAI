package whl.trending.updater

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VersionCompareTest {

    // 数字版本比较（原有行为，防回归）

    @Test
    fun higher_stable_is_newer() {
        assertTrue(isNewerVersion("0.22.0", "0.21.0"))
    }

    @Test
    fun same_stable_is_not_newer() {
        assertFalse(isNewerVersion("0.22.0", "0.22.0"))
    }

    @Test
    fun lower_stable_is_not_newer() {
        assertFalse(isNewerVersion("0.21.0", "0.22.0"))
    }

    @Test
    fun minor_carries_over_patch() {
        assertTrue(isNewerVersion("1.0.0", "0.99.99"))
    }

    @Test
    fun higher_prerelease_core_is_newer_than_lower_stable() {
        // 灰度用户从旧正式版升 beta：0.22.0-beta.1 > 0.21.0
        assertTrue(isNewerVersion("0.22.0-beta.1", "0.21.0"))
    }

    // 预发布后缀（semver：正式版 > 同号预发布版）

    @Test
    fun stable_is_newer_than_same_version_prerelease() {
        // 转正场景：装了 0.22.0-beta.1 的灰度用户必须收到 0.22.0 的更新提示
        assertTrue(isNewerVersion("0.22.0", "0.22.0-beta.1"))
    }

    @Test
    fun prerelease_is_not_newer_than_same_version_stable() {
        assertFalse(isNewerVersion("0.22.0-beta.1", "0.22.0"))
    }

    @Test
    fun same_prerelease_is_not_newer() {
        assertFalse(isNewerVersion("0.22.0-beta.1", "0.22.0-beta.1"))
    }

    @Test
    fun core_comparison_wins_over_suffix() {
        assertTrue(isNewerVersion("0.22.0", "0.21.9-beta.1"))
        assertFalse(isNewerVersion("0.21.9-beta.1", "0.22.0"))
    }
}
