package whl.trending.ai.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.back
import trendingai.shared.generated.resources.color_lab_discard
import trendingai.shared.generated.resources.color_lab_title
import wang.harlon.appsettings.appearance.ColorLabContent
import wang.harlon.appsettings.appearance.LocalAppearanceState
import wang.harlon.appsettings.appearance.rememberColorLabSession
import whl.trending.ai.ui.common.TrendingScaffold
import whl.trending.ai.ui.common.TrendingTopAppBar

/**
 * 调色台：实时生效、返回键不撤销，离开时由会话自动记历史并上报。
 * 撤销放顶栏而非页尾：用户正在上方拖色相、最需要反悔的那一刻，页尾按钮看不见也够不着。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorLabScreen(onBack: () -> Unit) {
    val session = rememberColorLabSession(LocalAppearanceState.current)

    TrendingScaffold(
        topBar = {
            TrendingTopAppBar(
                title = { Text(stringResource(Res.string.color_lab_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = session::undo, enabled = session.isDirty) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(Res.string.color_lab_discard))
                    }
                },
            )
        },
    ) { innerPadding ->
        ColorLabContent(session, modifier = Modifier.padding(innerPadding))
    }
}
