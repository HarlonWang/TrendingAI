package whl.trending.ai.ui.subscription

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import app.tinyui.updates.UpdatesPage
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.back
import whl.trending.ai.tinyui.TinyUIUpdates
import whl.trending.ai.tinyui.TrendingTinyUI
import whl.trending.ai.ui.common.TrendingScaffold
import whl.trending.ai.ui.common.TrendingTopAppBar

/**
 * 订阅页（付费墙本体）。触点是门，这一页是门后面的东西。
 *
 * 正文是 TinyUI 页 `trendingai/subscription`（源码在 ../trendingai-tinyui，可热下发）：拉价、文案、Pro 态、选档、
 * 下单、埋点都在页面包里，这里只出顶栏。三条刻意为之的约束（实现也在页面包里）：
 *
 * 1. **文案以服务端为准。** 标题、副标题、权益行、CTA、退款说明、致谢、失败提示来自
 *    app-config 的 `pro_paywall`，页面包的 i18n 只是从未拉到时的默认。约束见仓库 CLAUDE.md「订阅页文案」。
 *
 * 2. **不出现任何硬编码价格。** 价格由 `/api/billing/prices` 按访客所在地取——中国区是
 *    真·本地价（¥199）而不是 $39 的汇率换算，客户端猜不出来；拿不到就整页不报价，
 *    把定价交给收银台呈现，而不是显示一个可能不对的数字。
 *
 * 3. **不提 GitHub Sponsors。** 两条通道价格没对齐（Sponsors 明显更便宜且权益同档），
 *    并列展示等于把买家推去便宜那条。「想纯支持项目」的路径仍在关于页的捐赠入口里。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(onBack: () -> Unit) {
    // App 启动时已建好（TinyUIUpdates.start），这里通常一进来就有
    val updates by TinyUIUpdates.updates.collectAsState()

    TrendingScaffold(
        topBar = {
            TrendingTopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        updates?.let {
            UpdatesPage(
                updates = it,
                name = PAGE,
                host = TrendingTinyUI.host,
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
            )
        }
    }
}

private const val PAGE = "${TinyUIUpdates.PKG}/subscription"
