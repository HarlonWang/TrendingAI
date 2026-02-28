package whl.trending.updater

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import whl.trending.ai.update.globalUpdateChecker

@Composable
fun UpdateAwareContent(content: @Composable () -> Unit) {
    val updateViewModel: UpdateViewModel = viewModel()
    remember(updateViewModel) { globalUpdateChecker = updateViewModel }

    val updateInfo by updateViewModel.updateInfo.collectAsState()
    updateInfo?.let {
        UpdateDialog(it, onDismiss = { updateViewModel.dismissUpdate() })
    }

    content()
}
