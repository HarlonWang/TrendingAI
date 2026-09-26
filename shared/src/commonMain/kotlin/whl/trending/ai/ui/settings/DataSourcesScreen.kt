package whl.trending.ai.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import app.tinyui.updates.UpdatesPage
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.back
import trendingai.shared.generated.resources.data_sources_title
import whl.trending.ai.tinyui.TinyUIUpdates
import whl.trending.ai.tinyui.TrendingTinyUI
import whl.trending.ai.ui.common.TrendingScaffold
import whl.trending.ai.ui.common.TrendingTopAppBar

/**
 * 数据来源与更新 —— 三源 + Picks 的更新节奏与收录范围，全 app 唯一的口径文案载体。
 *
 * 正文是 TinyUI 页 `trendingai/data-sources`（源码在 ../trendingai-tinyui），文案随调度改时热下发即可；
 * 标题留原生，与设置入口共用 `data_sources_title`。
 *
 * 入口只有「设置 › 通用 › 数据来源与更新」一处：各列表尾部的时机行是纯展示
 * （见 [whl.trending.ai.ui.common.SourceMetaFooter]），挂一个没有图标提示的隐形可点区
 * 只会带来误触。刻意也不做源级 InfoDialog——那会让同一份口径文案在浮层和本页各存一份。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataSourcesScreen(onBack: () -> Unit) {
    val updates by TinyUIUpdates.updates.collectAsState()

    TrendingScaffold(
        topBar = {
            TrendingTopAppBar(
                title = { Text(stringResource(Res.string.data_sources_title)) },
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

private const val PAGE = "${TinyUIUpdates.PKG}/data-sources"
