package whl.trending.ai

import androidx.compose.runtime.Composable
import whl.trending.ai.core.Constants
import whl.trending.updater.UpdateAwareContent

// github 渠道：更新弹窗「下载」跳 GitHub Release 页，用户在此下载的仍是 github 包，
// 保证 channel 归因跨更新稳定（不会因走官网而漂移成 r2）。
@Composable
fun UpdateWrapper(content: @Composable () -> Unit) {
    UpdateAwareContent(downloadUrl = Constants.RELEASES_URL, content = content)
}
