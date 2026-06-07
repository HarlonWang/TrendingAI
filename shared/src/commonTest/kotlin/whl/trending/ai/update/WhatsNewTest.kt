package whl.trending.ai.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WhatsNewTest {

    // ---- shouldShowWhatsNew ----

    @Test
    fun first_install_does_not_show() {
        assertFalse(shouldShowWhatsNew("0.9.0", "0.9.0", lastSeenVersion = null))
    }

    @Test
    fun upgrade_with_matching_bundle_shows() {
        assertTrue(shouldShowWhatsNew("0.9.0", "0.9.0", lastSeenVersion = "0.8.0"))
    }

    @Test
    fun same_version_does_not_show() {
        assertFalse(shouldShowWhatsNew("0.9.0", "0.9.0", lastSeenVersion = "0.9.0"))
    }

    @Test
    fun bundled_placeholder_does_not_show() {
        // dev 构建 / CI 生成失败时内置文件还是 0.0.0 占位
        assertFalse(shouldShowWhatsNew("0.9.0", "0.0.0", lastSeenVersion = "0.8.0"))
    }

    @Test
    fun prerelease_suffix_requires_exact_match() {
        assertTrue(shouldShowWhatsNew("0.9.0-beta", "0.9.0-beta", lastSeenVersion = "0.8.0"))
        assertFalse(shouldShowWhatsNew("0.9.0-beta", "0.9.0", lastSeenVersion = "0.8.0"))
    }

    // ---- parseWhatsNew ----

    @Test
    fun parse_full_payload() {
        val info = parseWhatsNew(
            """{"version":"0.9.0","zh":["修复崩溃"],"en":["Fix crash"]}"""
        )
        assertEquals("0.9.0", info?.version)
        assertEquals(listOf("修复崩溃"), info?.zh)
        assertEquals(listOf("Fix crash"), info?.en)
        assertFalse(info!!.isEmpty)
    }

    @Test
    fun parse_placeholder_is_empty() {
        val info = parseWhatsNew("""{"version":"0.0.0","zh":[],"en":[]}""")
        assertTrue(info!!.isEmpty)
    }

    @Test
    fun parse_missing_fields_falls_back_to_defaults() {
        val info = parseWhatsNew("""{"version":"0.9.0"}""")
        assertEquals("0.9.0", info?.version)
        assertTrue(info!!.isEmpty)
    }

    @Test
    fun parse_ignores_unknown_keys() {
        val info = parseWhatsNew("""{"version":"0.9.0","zh":["a"],"en":["b"],"extra":1}""")
        assertEquals("0.9.0", info?.version)
    }

    @Test
    fun parse_invalid_json_returns_null() {
        assertNull(parseWhatsNew("not json"))
        assertNull(parseWhatsNew(""))
    }
}
