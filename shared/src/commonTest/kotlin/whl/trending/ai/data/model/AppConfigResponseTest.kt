package whl.trending.ai.data.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppConfigResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun decodes_min_version() {
        val config = json.decodeFromString<AppConfigResponse>("""{"min_version":"0.15.0"}""")
        assertEquals("0.15.0", config.minVersion)
    }

    @Test
    fun missing_min_version_defaults_to_null() {
        val config = json.decodeFromString<AppConfigResponse>("""{}""")
        assertNull(config.minVersion)
    }

    @Test
    fun explicit_null_min_version_decodes_to_null() {
        val config = json.decodeFromString<AppConfigResponse>("""{"min_version":null}""")
        assertNull(config.minVersion)
    }

    @Test
    fun ignores_unknown_keys_for_forward_compat() {
        val config = json.decodeFromString<AppConfigResponse>(
            """{"min_version":"0.15.0","latest_version":"0.16.0","message":"hi"}"""
        )
        assertEquals("0.15.0", config.minVersion)
    }
}
