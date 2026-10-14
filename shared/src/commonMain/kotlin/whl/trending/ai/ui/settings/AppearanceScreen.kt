package whl.trending.ai.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.appearance
import trendingai.shared.generated.resources.back
import wang.harlon.appsettings.appearance.AppearanceContent
import wang.harlon.appsettings.appearance.LocalAppearanceState
import whl.trending.ai.ui.common.TrendingScaffold
import whl.trending.ai.ui.common.TrendingTopAppBar
import whl.trending.ai.ui.theme.trackAppearanceEvent

/** 外观页：深浅模式、主题色、动态图标，整页内容在 appsettings-kmp；这里只套骨架、给图标档位与埋点。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(
    onBack: () -> Unit,
    onNavigateToColorLab: () -> Unit,
) {
    val appearance = LocalAppearanceState.current
    val icons = rememberTrendingAppIcons(onEvent = ::trackAppearanceEvent)

    TrendingScaffold(
        topBar = {
            TrendingTopAppBar(
                title = { Text(stringResource(Res.string.appearance)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back))
                    }
                }
            )
        }
    ) { innerPadding ->
        AppearanceContent(
            state = appearance,
            modifier = Modifier.padding(innerPadding),
            onOpenColorLab = onNavigateToColorLab,
            icons = icons,
        )
    }
}
