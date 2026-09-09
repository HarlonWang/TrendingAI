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

    @Test
    fun decodes_pro_paywall_partially_and_tolerates_missing_keys() {
        val config = json.decodeFromString<AppConfigResponse>(
            """{"pro_paywall":{"title":{"zh":"升级","en":"Upgrade"},"benefits":[{"icon":"quota","text":{"en":"More"}},{"text":{"en":"X"}}],"cta":{"sign_in":{"en":"Sign in"}}}}"""
        )
        val paywall = config.proPaywall!!
        assertEquals("升级", paywall.title!!.zh)
        assertNull(paywall.subtitle)
        assertEquals("quota", paywall.benefits[0].icon)
        assertNull(paywall.benefits[1].icon)
        assertEquals("Sign in", paywall.cta!!.signIn!!.en)
        assertNull(paywall.cta!!.subscribe)
        assertNull(paywall.refundNote)
    }

    @Test
    fun benefit_row_without_text_does_not_break_the_whole_config() {
        val config = json.decodeFromString<AppConfigResponse>(
            """{"min_version":"1.0.0","pro_paywall":{"benefits":[{"icon":"quota"}]}}"""
        )
        assertEquals("1.0.0", config.minVersion)
        assertNull(config.proPaywall!!.benefits.single().text)
    }
}
