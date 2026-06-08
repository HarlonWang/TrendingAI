package whl.trending.updater

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import whl.trending.ai.core.Constants
import whl.trending.ai.update.globalUpdateChecker

/**
 * @param downloadUrl 更新弹窗「下载」按钮的跳转地址，由各分发渠道（github / r2）按需注入，
 *   以保证 channel 归因跨更新稳定；默认官网。库本身保持渠道无关。
 */
@Composable
fun UpdateAwareContent(
    downloadUrl: String = Constants.OFFICIAL_WEBSITE_URL,
    content: @Composable () -> Unit
) {
    val updateViewModel: UpdateViewModel = viewModel()
    SideEffect { globalUpdateChecker = updateViewModel }

    val updateInfo by updateViewModel.updateInfo.collectAsState()
    updateInfo?.let {
        UpdateDialog(it, downloadUrl = downloadUrl, onDismiss = { updateViewModel.dismissUpdate() })
    }

    content()
}
