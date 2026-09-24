package whl.trending.ai.tinyui

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LoadingIndicatorDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tinyui.PageError
import app.tinyui.PageLocal
import app.tinyui.PageSink
import app.tinyui.TinyUIHost
import app.tinyui.components.registerBuiltins
import app.tinyui.schema.ComponentRegistry
import whl.trending.ai.data.repository.BillingRepository
import whl.trending.ai.tinyui.generated.HostSchemas

/**
 * 宿主对 TinyUI 页面的承诺变了（`ta.*` 组件、能力、tinyui 下限、引擎）就加 1，并生成 `shared/tinyui-host/<n>.txt`
 * （HostSnapshotTest，tinyui docs/updates.md §4.1）；单纯升 tinyui 不加。页面源码在 ../trendingai-tinyui。
 * 快照只认组件 schema 与能力名，以下变了测试不会拦、同样要加 1：能力的参数 / 返回形状 / 行为、页面 props（SubscriptionProps）、页面名、store key。
 */
const val HOST_VERSION = "5"

/** 当前屏的 SnackbarHostState，`ui.snackbar` 从这里取；挂页面的屏经 HostServices.locals 提供 */
val PageSnackbar = PageLocal<SnackbarHostState>()

object TrendingTinyUI {
    /** App 级，全 App 一份：组件与能力即宿主契约 */
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    val host: TinyUIHost by lazy {
        val components = ComponentRegistry().registerBuiltins().apply {
            register(HostSchemas.TaIcon) { scope ->
                Icon(
                    benefitIcon(scope["name"]),
                    contentDescription = null,
                    tint = scope.color("tint") ?: LocalContentColor.current,
                    modifier = scope.modifier().size(scope.get<Dp>("size") ?: 24.dp),
                )
            }
            register(HostSchemas.TaLoading) { scope ->
                LoadingIndicator(
                    modifier = scope.modifier().size(scope.get<Dp>("size") ?: 24.dp),
                    color = scope.color("color") ?: LoadingIndicatorDefaults.indicatorColor,
                )
            }
        }
        val sink = object : PageSink {
            override fun error(error: PageError) = println("TinyUI $error")
            override fun log(line: String) = println("TinyUI $line")
        }
        TinyUIHost(components, sink, capabilities(lazy { BillingRepository() }))
    }
}

/** key 与后端 lib/pro-paywall.js 对应；认不出的 key 用通用勾选，新行不必等客户端发版 */
private fun benefitIcon(key: String?): ImageVector = when (key) {
    "quota" -> Icons.Outlined.Bolt
    "models" -> Icons.Outlined.AutoAwesome
    "voice" -> Icons.Outlined.Mic
    "image_generation" -> Icons.Outlined.Brush
    "search" -> Icons.Outlined.TravelExplore
    else -> Icons.Outlined.CheckCircle
}
