package whl.trending.chat.engine.byok

import whl.trending.ai.data.local.globalSettingsManager

/**
 * BYOK 配置的读写聚合点：非敏感字段走 [globalSettingsManager]，apiKey 走 [SecureKeyStore]。
 * 引擎选择与设置 UI 都经此拿/存完整 [ByokConfig]，避免各处各自拼装。
 */
object ByokConfigStore {

    fun current(): ByokConfig {
        val ordinal = globalSettingsManager.getByokProviderOrdinal()
        val provider = ByokProvider.entries.getOrElse(ordinal) { ByokProvider.OPENAI_COMPATIBLE }
        return ByokConfig(
            enabled = globalSettingsManager.getByokEnabledSync(),
            provider = provider,
            baseUrl = globalSettingsManager.getByokBaseUrl(),
            apiKey = SecureKeyStore.getApiKey(),
            model = globalSettingsManager.getByokModel(),
        )
    }

    fun setEnabled(value: Boolean) = globalSettingsManager.setByokEnabled(value)
    fun setProvider(provider: ByokProvider) = globalSettingsManager.setByokProviderOrdinal(provider.ordinal)
    fun setBaseUrl(value: String) = globalSettingsManager.setByokBaseUrl(value.trim())
    fun setModel(value: String) = globalSettingsManager.setByokModel(value.trim())
    fun setApiKey(value: String) = SecureKeyStore.setApiKey(value.trim())
}
