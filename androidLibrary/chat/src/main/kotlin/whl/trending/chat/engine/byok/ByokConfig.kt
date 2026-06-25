package whl.trending.chat.engine.byok

/** BYOK 支持的协议族。 */
enum class ByokProvider {
    /** OpenAI `/chat/completions` 兼容（OpenAI / OpenRouter / DeepSeek / Ollama / 自建网关等）。 */
    OPENAI_COMPATIBLE,

    /** Anthropic 原生 `/v1/messages`。 */
    ANTHROPIC,
}

/**
 * 用户自定义 AI 模型配置（单套活动配置）。
 *
 * @param enabled “使用我自己的模型”开关；与 [isValid] 共同决定是否真正启用 BYOK 引擎
 * @param baseUrl 形如 `https://api.openai.com/v1` / `https://api.anthropic.com`
 * @param apiKey 敏感凭证，加密落盘
 * @param model 选中的模型 id
 */
data class ByokConfig(
    val enabled: Boolean,
    val provider: ByokProvider,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
) {
    /** 配置是否完整可用；只看完整性，不看 [enabled] 开关。 */
    val isValid: Boolean
        get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()
}

/** 埋点用的低基数 provider 名（不泄露任何用户配置）。 */
fun ByokProvider.analyticsName(): String = when (this) {
    ByokProvider.OPENAI_COMPATIBLE -> "openai"
    ByokProvider.ANTHROPIC -> "anthropic"
}
