package whl.trending.ai.core

import androidx.compose.ui.window.ComposeUIViewController
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform
import platform.UIKit.UIViewController
import wang.harlon.eventbase.Eventbase
import wang.harlon.eventbase.init
import whl.trending.ai.auth.initLoginbaseAuth
import whl.trending.ai.auth.installIosOAuthLauncher
import whl.trending.ai.chat.installTrendingChatHost
import whl.trending.ai.core.analytics.analyticsConfig

/**
 * iOS 侧没有 Application 那一层，埋点就在这儿初始化——[Eventbase.init] 先到先得，
 * 重建 controller 不会叠加生命周期观察者。
 */
@OptIn(ExperimentalNativeApi::class)
fun MainViewController(): UIViewController {
    // 没有 BuildConfig 可读，用运行时的二进制类型；写死 false 会把调试流量算进生产读数
    Eventbase.init(analyticsConfig(isDebug = Platform.isDebugBinary))
    installTrendingChatHost()
    initLoginbaseAuth()
    installIosOAuthLauncher()
    return ComposeUIViewController { App() }
}
