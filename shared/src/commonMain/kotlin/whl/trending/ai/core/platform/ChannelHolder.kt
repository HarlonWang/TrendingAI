package whl.trending.ai.core.platform

/**
 * 分发渠道承接点。
 *
 * 渠道值（github / r2 / play / fdroid）来自 androidApp 的 `BuildConfig.CHANNEL`，属 Android 平台专有，
 * shared（commonMain）无法直接引用。因此由 Android 在启动时（[whl.trending.ai.TrendingApplication]）
 * 写入此处，shared 侧的埋点（[trackEvent]）与请求 UA（[getUserAgent]）再统一读取。
 *
 * 其他平台（iOS）不写入，保持默认 [UNKNOWN]。set 一次发生在任何读取之前，无并发问题。
 */
object ChannelHolder {
    const val UNKNOWN: String = "unknown"

    private var channel: String = UNKNOWN

    fun set(value: String) {
        channel = value
    }

    fun get(): String = channel
}
