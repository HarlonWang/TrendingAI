package whl.trending.ai.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.favorites_action_failed
import whl.trending.ai.data.repository.globalFavoriteRepository

/**
 * 全局收藏失败提示宿主：已登录时服务端是收藏的唯一真源，收藏/取消打不通接口就会回滚，
 * 用户必须被告知这次操作没生效（否则只看到星星自己弹回去）。
 *
 * 用 Snackbar 而非弹窗：收藏是轻操作，失败也不阻塞任何流程，提示完即走。
 * 放在 App 根部（与 SignInHintHost 平级），因为收藏入口散落在 Picks / Feed / Trending / 收藏列表四处，
 * 逐页接 SnackbarHost 只要漏一处那页就静默失败。Box 只做定位，不拦截触摸。
 */
@Composable
fun FavoriteErrorHost() {
    val snackbarHostState = remember { SnackbarHostState() }
    val message = stringResource(Res.string.favorites_action_failed)

    LaunchedEffect(Unit) {
        globalFavoriteRepository.errors.collect {
            snackbarHostState.showSnackbar(message)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}
