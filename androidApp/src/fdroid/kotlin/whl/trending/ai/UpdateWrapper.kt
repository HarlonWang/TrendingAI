package whl.trending.ai

import androidx.compose.runtime.Composable

// F-Droid 渠道：不含自建 updater（F-Droid 政策禁止应用自行下载可执行文件更新，
// 由 F-Droid 客户端统一管理更新），因此直接渲染内容。
@Composable
fun UpdateWrapper(content: @Composable () -> Unit) {
    content()
}
