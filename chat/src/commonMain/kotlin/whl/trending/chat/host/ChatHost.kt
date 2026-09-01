package whl.trending.chat.host

import androidx.compose.runtime.Composable
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import kotlinx.coroutines.flow.Flow
import whl.trending.chat.model.ChatModelsResponse

/** AI 请求种类，宿主负责映射到自己的埋点词汇。 */
enum class ChatAiKind { CHAT, RESEARCH }

/** AI 请求终态，与 [ChatAiKind] 配套。 */
enum class ChatAiOutcome { OK, ERROR, INTERRUPTED }

/** SDK 上报给宿主的埋点事件；Requested 与 Completed 一次请求恰好各一条。 */
sealed interface ChatAiEvent {
    data class Requested(
        val kind: ChatAiKind,
        val from: String,
        val imageCount: Int? = null,
    ) : ChatAiEvent

    data class Completed(
        val kind: ChatAiKind,
        val outcome: ChatAiOutcome,
        val durationMs: Long? = null,
        val reason: String? = null,
        val tier: String? = null,
    ) : ChatAiEvent
}

/**
 * chat SDK 的宿主契约：登录、档位、偏好持久化、埋点、网络鉴权全部由宿主注入，
 * SDK 对宿主 app 零依赖。接入方在任何 chat UI/引擎被触达之前给 [chatHost] 赋值
 * （示例见 sample/DemoChatHost）。
 */
interface ChatHost {

    /** 宿主是否具备登录能力；false 时 SDK 隐藏一切登录入口（按匿名档运行）。 */
    val canSignIn: Boolean

    /** 当前是否已登录（会话未恢复完成时按未登录处理）。 */
    fun isLoggedInNow(): Boolean

    /** 登录态变化流，配合 [isLoggedInNow] 作首帧初值。 */
    val isLoggedIn: Flow<Boolean>

    /** 唤起宿主登录流程。[source] 为入口标识（如 "chat_welcome"），供宿主埋点归因。 */
    fun signIn(source: String)

    /** 当前是否 Pro 档（宿主本地缓存口径，不打网络）。 */
    fun currentIsPro(): Boolean

    val isPro: Flow<Boolean>

    /** 模型选择偏好（宿主持久化）。空串表示跟随服务端默认，见 [whl.trending.chat.model.FOLLOW_SERVER_DEFAULT]。 */
    val chatModelChoice: Flow<String>

    fun currentChatModelChoice(): String

    /** 钉住某个具体模型 id。 */
    fun pinChatModel(id: String)

    /** 解除钉住，回到跟随服务端默认。 */
    fun followServerDefault()

    /** 单条消息可附图上限。 */
    fun imagesMaxCount(): Int

    /** 单图压缩预算（KB）。 */
    fun imagesPerImageJpegKb(): Int

    /** 安装标识，随请求头 `X-Install-Id` 透传（服务端限流维度）。 */
    fun installId(): String

    /** 本次请求应使用的语言（"zh" / "en"），由宿主按 app 语言设置与系统语言解析。 */
    suspend fun requestLang(): String

    /** 拉取聊天模型目录，缓存策略归 SDK（[whl.trending.chat.model.ChatModelsProvider]）。 */
    suspend fun fetchChatModels(): ChatModelsResponse

    /** 给 SDK 的 HttpClient 装鉴权（带 token、401 刷新等），无登录能力的宿主可空实现。 */
    fun configureHttpAuth(config: HttpClientConfig<*>)

    /** SDK 建好的 client 交宿主登记（如登出时统一清 token 缓存），默认不登记。 */
    fun registerAuthorizedClient(client: HttpClient) {}

    /** SDK 埋点出口，宿主映射到自己的事件词汇后上报。 */
    fun onAiEvent(event: ChatAiEvent)

    /** Pro 档徽章 slot；null 时 SDK 不渲染徽章位。 */
    val proBadge: (@Composable () -> Unit)?
}

lateinit var chatHost: ChatHost

fun isChatHostInstalled(): Boolean = ::chatHost.isInitialized
