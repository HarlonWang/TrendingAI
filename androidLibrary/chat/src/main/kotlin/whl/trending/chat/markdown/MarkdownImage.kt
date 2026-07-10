package whl.trending.chat.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import whl.trending.chat.R

/**
 * 助手 Markdown 里的块级网络图片:
 * - 加载中:全宽 surfaceVariant 占位块,避免高度从 0 跳变
 * - 成功:按原始宽高比展示(高度上限 280dp),点击回调交给上层开全屏查看
 * - 失败:降级为可点链接(alt 或 URL),点击跳浏览器
 */
@Composable
internal fun MarkdownImage(
    url: String,
    alt: String,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var state by remember(url) { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
    if (state is AsyncImagePainter.State.Error) {
        val uriHandler = LocalUriHandler.current
        Text(
            text = alt.ifBlank { url },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
            modifier = modifier.clickable { uriHandler.openUri(url) },
        )
        return
    }
    val image = (state as? AsyncImagePainter.State.Success)?.result?.image
    val ratio = image?.takeIf { it.height > 0 }?.let { it.width.toFloat() / it.height }
    val loading = ratio == null
    AsyncImage(
        model = url,
        contentDescription = alt.ifBlank { stringResource(R.string.chat_user_image) },
        contentScale = ContentScale.Fit,
        onState = { state = it },
        modifier = modifier
            .heightIn(max = 280.dp)
            .then(
                if (loading) {
                    Modifier.fillMaxWidth().height(180.dp)
                } else {
                    Modifier.aspectRatio(ratio)
                },
            )
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (loading) {
                    Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                } else {
                    Modifier.clickable { onClick(url) }
                },
            ),
    )
}
