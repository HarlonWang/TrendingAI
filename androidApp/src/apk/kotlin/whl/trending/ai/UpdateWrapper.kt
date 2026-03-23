package whl.trending.ai

import androidx.compose.runtime.Composable
import whl.trending.updater.UpdateAwareContent

@Composable
fun UpdateWrapper(content: @Composable () -> Unit) {
    UpdateAwareContent(content)
}
