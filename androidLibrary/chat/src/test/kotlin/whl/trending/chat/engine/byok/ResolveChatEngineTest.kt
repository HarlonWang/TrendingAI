package whl.trending.chat.engine.byok

import whl.trending.chat.engine.ChatApi
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertSame

/** [resolveChatEngine] 选择逻辑：齐全且启用→BYOK，否则回落后端共享引擎。 */
class ResolveChatEngineTest {

    private fun cfg(enabled: Boolean, apiKey: String = "sk", model: String = "gpt-4o") =
        ByokConfig(enabled, ByokProvider.OPENAI_COMPATIBLE, "https://x/v1", apiKey, model)

    @Test fun enabled_and_valid_uses_byok() {
        assertIs<ByokChatEngine>(resolveChatEngine(cfg(enabled = true)))
    }

    @Test fun disabled_falls_back_to_backend() {
        assertSame(ChatApi.shared, resolveChatEngine(cfg(enabled = false)))
    }

    @Test fun enabled_but_incomplete_falls_back_to_backend() {
        assertSame(ChatApi.shared, resolveChatEngine(cfg(enabled = true, apiKey = "")))
    }
}
