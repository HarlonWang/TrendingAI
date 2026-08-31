package whl.trending.chat.sample

import io.ktor.client.HttpClientConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import whl.trending.chat.host.ChatAiEvent
import whl.trending.chat.host.ChatHost
import whl.trending.chat.model.ChatModelsResponse
import whl.trending.chat.model.FOLLOW_SERVER_DEFAULT

/**
 * 无宿主运行的最小契约实现（Demo/独立验收用）：匿名档、无登录能力、埋点丢弃、
 * 模型选择进程内存活。真实宿主的接线示例见 TrendingAI shared 的 TrendingChatHost。
 */
object DemoChatHost : ChatHost {
    override val canSignIn = false
    override fun isLoggedInNow() = false
    override val isLoggedIn: Flow<Boolean> = flowOf(false)
    override fun signIn(source: String) {}

    override fun currentIsPro() = false
    override val isPro: Flow<Boolean> = flowOf(false)

    private val modelChoice = MutableStateFlow(FOLLOW_SERVER_DEFAULT)
    override val chatModelChoice: Flow<String> get() = modelChoice
    override fun currentChatModelChoice() = modelChoice.value
    override fun pinChatModel(id: String) {
        modelChoice.value = id
    }

    override fun followServerDefault() {
        modelChoice.value = FOLLOW_SERVER_DEFAULT
    }

    override fun imagesMaxCount() = 9
    override fun imagesPerImageJpegKb() = 300
    override fun installId() = "demo"
    override suspend fun requestLang() = "en"
    override suspend fun fetchChatModels() = ChatModelsResponse()

    override fun configureHttpAuth(config: HttpClientConfig<*>) {}
    override fun onAiEvent(event: ChatAiEvent) {}
    override val proBadge: (@androidx.compose.runtime.Composable () -> Unit)? = null
}
