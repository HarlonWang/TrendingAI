package whl.trending.ai.chat

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import whl.trending.ai.auth.AuthState
import whl.trending.ai.auth.globalAuthManager
import whl.trending.ai.core.analytics.AiKind
import whl.trending.ai.core.analytics.AiOutcome
import whl.trending.ai.core.analytics.AppEvent
import whl.trending.ai.core.analytics.track
import whl.trending.ai.core.platform.getSystemLanguage
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.data.remote.TrendingApi
import whl.trending.ai.data.remote.installTrendingAuth
import whl.trending.ai.data.remote.trackAuthTokenCache
import whl.trending.ai.ui.profile.ProBadge
import whl.trending.chat.host.ChatAiEvent
import whl.trending.chat.host.ChatAiKind
import whl.trending.chat.host.ChatAiOutcome
import whl.trending.chat.host.ChatHost
import whl.trending.chat.host.chatHost
import whl.trending.chat.model.ChatModelsResponse

/**
 * chat SDK 宿主契约的 TrendingAI 实现：登录接 [globalAuthManager]、偏好接
 * [globalSettingsManager]、埋点映射回 [AppEvent] 词汇（事件表本身不变）。
 * 在 chat 任何 UI/引擎被触达之前调用 [installTrendingChatHost]（幂等）。
 */
private object TrendingChatHost : ChatHost {
    private val modelsApi = TrendingApi()

    override val canSignIn: Boolean get() = globalAuthManager.isSupported
    override fun isLoggedInNow() = globalAuthManager.authState.value is AuthState.LoggedIn
    override val isLoggedIn: Flow<Boolean> = globalAuthManager.authState.map { it is AuthState.LoggedIn }
    override fun signIn(source: String) = globalAuthManager.signIn(source)

    override fun currentIsPro() = globalSettingsManager.currentIsPro()
    override val isPro: Flow<Boolean> get() = globalSettingsManager.isPro
    override val chatModelChoice: Flow<String> get() = globalSettingsManager.chatModelChoice
    override fun currentChatModelChoice() = globalSettingsManager.currentChatModelChoice()
    override fun pinChatModel(id: String) = globalSettingsManager.pinChatModel(id)
    override fun followServerDefault() = globalSettingsManager.followServerDefault()

    override fun imagesMaxCount() = globalSettingsManager.chatImagesMaxCount()
    override fun imagesPerImageJpegKb() = globalSettingsManager.chatImagesPerImageJpegKb()
    override fun installId() = globalSettingsManager.getOrCreateInstallId()

    /** 仅 app 语言为中文（或跟随系统且系统为中文）时用 zh，其余 en（与后端默认一致）。 */
    override suspend fun requestLang(): String {
        val appLang = globalSettingsManager.appLanguage.first()
        return appLang.isoCode ?: if (getSystemLanguage() == "zh") "zh" else "en"
    }

    override suspend fun fetchChatModels(): ChatModelsResponse = modelsApi.fetchChatModels()

    override fun configureHttpAuth(config: HttpClientConfig<*>) = config.installTrendingAuth()
    override fun registerAuthorizedClient(client: HttpClient) {
        client.trackAuthTokenCache()
    }

    override fun onAiEvent(event: ChatAiEvent) = track(
        when (event) {
            is ChatAiEvent.Requested -> AppEvent.AiRequested(
                kind = event.kind.toAiKind(),
                from = event.from,
                imageCount = event.imageCount,
            )
            is ChatAiEvent.Completed -> AppEvent.AiCompleted(
                kind = event.kind.toAiKind(),
                outcome = when (event.outcome) {
                    ChatAiOutcome.OK -> AiOutcome.OK
                    ChatAiOutcome.ERROR -> AiOutcome.ERROR
                    ChatAiOutcome.INTERRUPTED -> AiOutcome.INTERRUPTED
                },
                durationMs = event.durationMs,
                reason = event.reason,
                tier = event.tier,
            )
        },
    )

    private fun ChatAiKind.toAiKind(): AiKind = when (this) {
        ChatAiKind.CHAT -> AiKind.CHAT
        ChatAiKind.RESEARCH -> AiKind.RESEARCH
    }

    override val proBadge: (@androidx.compose.runtime.Composable () -> Unit) = { ProBadge() }
}

fun installTrendingChatHost() {
    chatHost = TrendingChatHost
}
