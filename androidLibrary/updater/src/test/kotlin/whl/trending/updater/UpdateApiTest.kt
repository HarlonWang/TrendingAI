package whl.trending.updater

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import whl.trending.ai.update.WhatsNewInfo

class UpdateApiTest {

    // parseLatestRelease

    @Test
    fun parse_release_with_whatsnew_asset() {
        val release = parseLatestRelease(
            """
            {
              "tag_name": "0.21.0",
              "assets": [
                {"name": "app.apk", "browser_download_url": "https://example.com/app.apk"},
                {"name": "whatsnew.json", "browser_download_url": "https://example.com/whatsnew.json"}
              ]
            }
            """.trimIndent()
        )
        assertEquals("0.21.0", release?.tagName)
        assertEquals("https://example.com/whatsnew.json", release?.whatsNewAssetUrl)
    }

    @Test
    fun parse_release_without_whatsnew_asset() {
        val release = parseLatestRelease(
            """{"tag_name":"0.21.0","assets":[{"name":"app.apk","browser_download_url":"https://example.com/app.apk"}]}"""
        )
        assertEquals("0.21.0", release?.tagName)
        assertNull(release?.whatsNewAssetUrl)
    }

    @Test
    fun parse_release_missing_assets_field() {
        val release = parseLatestRelease("""{"tag_name":"0.21.0"}""")
        assertEquals("0.21.0", release?.tagName)
        assertNull(release?.whatsNewAssetUrl)
    }

    @Test
    fun parse_release_ignores_unknown_keys() {
        val release = parseLatestRelease(
            """{"tag_name":"0.21.0","body":"### notes","draft":false,"assets":[]}"""
        )
        assertEquals("0.21.0", release?.tagName)
    }

    @Test
    fun parse_invalid_json_returns_null() {
        assertNull(parseLatestRelease("not json"))
        assertNull(parseLatestRelease(""))
    }

    // acceptWhatsNew

    @Test
    fun accept_matching_version_with_content() {
        val info = WhatsNewInfo("0.21.0", zh = listOf("修复崩溃"), en = listOf("Fix crash"))
        assertEquals(info, acceptWhatsNew(info, "0.21.0"))
    }

    @Test
    fun reject_version_mismatch() {
        val info = WhatsNewInfo("0.20.0", zh = listOf("旧内容"), en = emptyList())
        assertNull(acceptWhatsNew(info, "0.21.0"))
    }

    @Test
    fun reject_empty_content() {
        val info = WhatsNewInfo("0.21.0", zh = emptyList(), en = emptyList())
        assertNull(acceptWhatsNew(info, "0.21.0"))
    }

    @Test
    fun reject_null_info() {
        assertNull(acceptWhatsNew(null, "0.21.0"))
    }
}
