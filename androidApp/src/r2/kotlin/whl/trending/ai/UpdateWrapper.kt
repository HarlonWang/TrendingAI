package whl.trending.ai

import androidx.compose.runtime.Composable
import whl.trending.ai.core.Constants
import whl.trending.updater.UpdateAwareContent

// r2 渠道：更新弹窗「下载」跳官网（官网分发 R2 包），用户更新后仍是 r2 包，
// 保证 channel 归因跨更新稳定。
@Composable
fun UpdateWrapper(content: @Composable () -> Unit) {
    UpdateAwareContent(downloadUrl = Constants.OFFICIAL_WEBSITE_URL, content = content)
}
