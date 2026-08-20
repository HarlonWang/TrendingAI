package whl.trending.ai.core.analytics

import wang.harlon.eventbase.Eventbase
import wang.harlon.eventbase.EventbaseConfig
import whl.trending.ai.core.platform.ChannelHolder
import whl.trending.ai.core.platform.getAppVersion
import whl.trending.ai.core.platform.getSystemLocaleTag
import whl.trending.ai.core.platform.isIosPlatform
import whl.trending.ai.data.local.globalSettingsManager

/** 自建埋点（eventbase）的摄取入口，`/e` 由库拼接。 */
private const val ENDPOINT = "https://api.trendingai.cn/t"

/** 公开 key，只用于路由与开关，进 APK 无妨（见 eventbase docs/protocol.md）。 */
private const val APP_KEY = "ta-8f3c9d21"

/**
 * [Eventbase.init] 的入参。**必须在 ChannelHolder.set 之后调**，否则渠道恒为 unknown。
 *
 * `installId` 传 App 自己那个：服务端按同一个 id 补发配额拦截与成单事件，
 * 两端各生成一个就再也串不成漏斗。
 */
fun analyticsConfig(isDebug: Boolean): EventbaseConfig = EventbaseConfig(
    endpoint = ENDPOINT,
    appKey = APP_KEY,
    appVersion = getAppVersion(),
    platform = if (isIosPlatform()) "ios" else "android",
    channel = ChannelHolder.get(),
    locale = getSystemLocaleTag(),
    isDebug = isDebug,
    logEvents = isDebug,
    installId = globalSettingsManager.getOrCreateInstallId(),
)

/** 全 App 唯一的上报出口。[flow] 用于跨进程漏斗（OAuth 回跳），见 [Eventbase.startFlow]。 */
fun track(event: AppEvent, flow: String? = null) {
    Eventbase.track(event, flow)
}

/** 登录成功后关联账号：此后事件带 user_id，并在服务端建立 install↔identity 映射。 */
fun setAnalyticsUser(userId: String?) {
    Eventbase.setUserId(userId)
}
