package whl.trending.ai.core

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController
import wang.harlon.eventbase.Eventbase
import wang.harlon.eventbase.init
import whl.trending.ai.core.analytics.analyticsConfig

/**
 * iOS 侧没有 Application 那一层，埋点就在这儿初始化——[Eventbase.init] 先到先得，
 * 重建 controller 不会叠加生命周期观察者。
 */
fun MainViewController(): UIViewController {
    Eventbase.init(analyticsConfig(isDebug = false))
    return ComposeUIViewController { App() }
}
