package whl.trending.chat.engine.byok

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [ByokConfig.isValid] 纯逻辑单测：齐全才有效，缺任一关键字段即无效。 */
class ByokConfigTest {

    private fun cfg(
        enabled: Boolean = true,
        provider: ByokProvider = ByokProvider.OPENAI_COMPATIBLE,
        baseUrl: String = "https://api.openai.com/v1",
        apiKey: String = "sk-xxx",
        model: String = "gpt-4o",
    ) = ByokConfig(enabled, provider, baseUrl, apiKey, model)

    @Test fun full_config_is_valid() {
        assertTrue(cfg().isValid)
    }

    @Test fun blank_baseUrl_is_invalid() {
        assertFalse(cfg(baseUrl = "  ").isValid)
    }

    @Test fun blank_apiKey_is_invalid() {
        assertFalse(cfg(apiKey = "").isValid)
    }

    @Test fun blank_model_is_invalid() {
        assertFalse(cfg(model = "").isValid)
    }

    @Test fun isValid_ignores_enabled_flag() {
        // isValid 只看配置完整性，不看开关；开关单独决定是否启用
        assertTrue(cfg(enabled = false).isValid)
    }
}
