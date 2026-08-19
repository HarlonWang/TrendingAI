package whl.trending.ai.core.analytics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/** 页面浏览上报。新增页面只多这一行加一个 [Screen] 常量，不新增事件名。 */
@Composable
fun TrackScreen(screen: Screen, from: String? = null) {
    LaunchedEffect(screen, from) { track(AppEvent.ScreenViewed(screen, from)) }
}
