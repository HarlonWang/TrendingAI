package whl.trending.ai

import android.app.Application
import wang.harlon.eventbase.Eventbase
import wang.harlon.eventbase.init
import whl.trending.ai.core.analytics.analyticsConfig
import whl.trending.ai.core.platform.AndroidContextHolder
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
        // 同理，且同样是「漏了就静默错」：getAppVersion() 拿不到 context 就只能回落。
        // 这里是全 app 唯一的初始化点，且必须早于下面读 getAppVersion() 的埋点配置——
        // 1.4.0 首日它还在 MainActivity（晚于本方法），全部事件的 app_version 因此恒为
        // 当时的兜底值 1.0.0，版本切片直接作废（读数见父仓 eventbase-首发读数-2026-08-22.md）
        AndroidContextHolder.initialize(this)
        Eventbase.init(context = this, config = analyticsConfig(isDebug = BuildConfig.DEBUG))
    }
}
