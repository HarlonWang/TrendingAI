package whl.trending.ai

import android.app.Application
import wang.harlon.eventbase.Eventbase
import wang.harlon.eventbase.init
import whl.trending.ai.core.analytics.analyticsConfig
import whl.trending.ai.core.platform.ChannelHolder

/**
 * app_opened / app_backgrounded 与会话时长全部由 eventbase-kt 自己算——它挂
 * ProcessLifecycleOwner，无界面的后台唤醒进程不会造出空会话（1.2.0 把日活推高 55%
 * 的那个坑，口径已定死在库里）。这里只负责初始化。
 */
class TrendingApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // 须在任何埋点 / 网络请求之前：埋点配置与 UA 都读它
        ChannelHolder.set(BuildConfig.CHANNEL)
        Eventbase.init(context = this, config = analyticsConfig(isDebug = BuildConfig.DEBUG))
    }
}
