package whl.trending.updater

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import whl.trending.ai.update.globalUpdateChecker

/**
 * @param downloadUrl 更新弹窗「下载」按钮的跳转地址，由各分发渠道（github / r2）显式注入，
 *   以保证 channel 归因跨更新稳定。库本身对渠道无感，不内置默认值。
 */
@Composable
fun UpdateAwareContent(
    downloadUrl: String,
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
