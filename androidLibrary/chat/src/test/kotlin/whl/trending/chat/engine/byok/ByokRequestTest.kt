package whl.trending.chat.engine.byok

import whl.trending.ai.chat.ChatContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** BYOK 请求构建的纯逻辑单测：URL 拼接 + system prompt。 */
class ByokRequestTest {

    @Test fun openai_chat_url_appends_path() {
        assertEquals(
            "https://api.openai.com/v1/chat/completions",
            ByokUrls.chatCompletions("https://api.openai.com/v1"),
        )
    }

    @Test fun chat_url_tolerates_trailing_slash() {
        assertEquals(
            "https://api.openai.com/v1/chat/completions",
            ByokUrls.chatCompletions("https://api.openai.com/v1/"),
        )
    }

    @Test fun openai_models_url() {
        assertEquals("https://x.ai/v1/models", ByokUrls.openAiModels("https://x.ai/v1"))
    }

    @Test fun anthropic_messages_url() {
        assertEquals("https://api.anthropic.com/v1/messages", ByokUrls.anthropicMessages("https://api.anthropic.com"))
    }

    @Test fun anthropic_models_url() {
        assertEquals("https://api.anthropic.com/v1/models", ByokUrls.anthropicModels("https://api.anthropic.com/"))
    }

    @Test fun missing_scheme_defaults_to_https() {
        assertEquals(
            "https://api.openai.com/v1/chat/completions",
            ByokUrls.chatCompletions("api.openai.com/v1"),
        )
    }

    @Test fun whitespace_is_trimmed() {
        assertEquals(
            "https://api.openai.com/v1/models",
            ByokUrls.openAiModels("  https://api.openai.com/v1  "),
        )
    }

    @Test fun explicit_http_localhost_is_preserved() {
        assertEquals("http://localhost:11434/models", ByokUrls.openAiModels("http://localhost:11434"))
    }

    @Test fun system_prompt_includes_chinese_instruction() {
        val s = ByokPrompt.system(context = null, lang = "zh")
        assertTrue(s.contains("中文"), "zh 应要求用中文回复，实际: $s")
    }

    @Test fun system_prompt_includes_context_title_and_summary() {
        val ctx = ChatContext(title = "Foobar", summary = "a cool tool", sourceUrl = "https://github.com/x/foobar")
        val s = ByokPrompt.system(context = ctx, lang = "en")
        assertTrue(s.contains("Foobar"))
        assertTrue(s.contains("a cool tool"))
    }
}
