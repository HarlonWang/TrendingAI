package whl.trending.chat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import okio.Path.Companion.toPath
import whl.trending.chat.core.logWarn
import trendingai.chat.generated.resources.Res
import trendingai.chat.generated.resources.chat_error_auth_invalid
import trendingai.chat.generated.resources.chat_error_bad_request
import trendingai.chat.generated.resources.chat_error_content_too_long
import trendingai.chat.generated.resources.chat_error_images_require_login
import trendingai.chat.generated.resources.chat_error_images_too_large
import trendingai.chat.generated.resources.chat_error_message
import trendingai.chat.generated.resources.chat_error_network
import trendingai.chat.generated.resources.chat_error_quota_global
import trendingai.chat.generated.resources.chat_error_region_blocked
import trendingai.chat.generated.resources.chat_error_server
import trendingai.chat.generated.resources.chat_error_timeout
import trendingai.chat.generated.resources.chat_quota_exceeded
import trendingai.chat.generated.resources.chat_retry
import trendingai.chat.generated.resources.chat_searching
import trendingai.chat.generated.resources.chat_share
import trendingai.chat.generated.resources.chat_user_image
import whl.trending.chat.markdown.MarkdownText
import whl.trending.chat.model.ChatError
import whl.trending.chat.model.ChatErrorCategory
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.SuggestionChip
import androidx.compose.ui.platform.LocalUriHandler
import whl.trending.chat.model.ChatMessage
import whl.trending.chat.model.SourceRef
import whl.trending.chat.model.Role

/**
 * 单条消息：
 * - 用户：右侧气泡（primaryContainer）
 * - 助手：左侧全宽 Markdown 渲染（无气泡，贴近 Claude/ChatGPT 风格）
 * 均支持长按选中复制；助手出错时展示错误与重试按钮。
 */
@Composable
fun MessageItem(
    message: ChatMessage,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (message.role) {
        Role.USER -> UserMessage(message, modifier)
        Role.ASSISTANT -> AssistantMessage(message, onRetry, modifier)
    }
}

@Composable
private fun UserMessage(message: ChatMessage, modifier: Modifier = Modifier) {
    var viewerPath by remember { mutableStateOf<String?>(null) }
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (message.images.isNotEmpty()) {
            UserImages(images = message.images, onImageClick = { viewerPath = it })
        }
        if (message.content.isNotBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.widthIn(max = 300.dp),
            ) {
                SelectionContainer {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
    viewerPath?.let { path ->
        ImageViewerDialog(model = path.toPath(), onDismiss = { viewerPath = null })
    }
}

/** 用户消息的图片区：单张放大展示，多张两列网格；点击进全屏查看器。 */
@Composable
private fun UserImages(images: List<String>, onImageClick: (String) -> Unit) {
    if (images.size == 1) {
        UserImageThumb(
            path = images[0],
            onClick = { onImageClick(images[0]) },
            modifier = Modifier.widthIn(max = 220.dp).heightIn(min = 120.dp, max = 280.dp),
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.End) {
        images.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { path ->
                    UserImageThumb(
                        path = path,
                        onClick = { onImageClick(path) },
                        modifier = Modifier.size(140.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun UserImageThumb(path: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AsyncImage(
        model = path.toPath(),
        contentDescription = stringResource(Res.string.chat_user_image),
        contentScale = ContentScale.Crop,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    )
}

@Composable
private fun AssistantMessage(
    message: ChatMessage,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        val error = message.error
        if (error == null) {
            // 联网搜索瞬态指示（M3 Expressive LoadingIndicator，全 app 统一）
            @OptIn(ExperimentalMaterial3ExpressiveApi::class)
            if (message.searching) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LoadingIndicator(modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(Res.string.chat_searching),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            var viewerUrl by remember { mutableStateOf<String?>(null) }
            SelectionContainer {
                MarkdownText(
                    markdown = message.content,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    onImageClick = { viewerUrl = it },
                )
            }
            viewerUrl?.let { url ->
                ImageViewerDialog(model = url, onDismiss = { viewerUrl = null })
            }
            if (message.sources.isNotEmpty()) {
                SourcesRow(message.sources)
            }
            Row {
                CopyIconButton(
                    text = message.content,
                    modifier = Modifier.size(32.dp),
                )
                val share = rememberShareText()
                IconButton(
                    onClick = { share(message.content) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = stringResource(Res.string.chat_share),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        } else if (error.code == ChatError.CODE_QUOTA_DEVICE) {
            // 个人配额触顶走专属卡片（登录 CTA / 纯提示），全局熔断仍走普通错误文案
            QuotaLimitCard(error = error, onRetry = onRetry)
        } else {
            Text(
                text = stringResource(errorMessageRes(error)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            // 地区拒绝虽是 5xx（category 可重试），但重试必然同样被拒——文案已说明「重试无效」，
            // 按钮再留着就是把用户按在一个必失败的循环里
            if (error.category.retryable && error.code != ChatError.CODE_REGION_BLOCKED) {
                TextButton(onClick = onRetry) {
                    Text(stringResource(Res.string.chat_retry))
                }
            }
        }
    }
}

/** 选具体文案：优先服务端 [ChatError.code]，未知则回落到 [ChatError.category]。 */
private fun errorMessageRes(error: ChatError): StringResource = when (error.code) {
    "auth_invalid" -> Res.string.chat_error_auth_invalid
    "images_require_login" -> Res.string.chat_error_images_require_login
    "content_too_long" -> Res.string.chat_error_content_too_long
    "image_too_large", "images_too_large" -> Res.string.chat_error_images_too_large
    "quota_global" -> Res.string.chat_error_quota_global
    ChatError.CODE_QUOTA_DEVICE -> Res.string.chat_quota_exceeded
    "upstream_timeout" -> Res.string.chat_error_timeout
    ChatError.CODE_REGION_BLOCKED -> Res.string.chat_error_region_blocked
    "upstream_error" -> Res.string.chat_error_server
    else -> when (error.category) {
        ChatErrorCategory.NETWORK -> Res.string.chat_error_network
        ChatErrorCategory.TIMEOUT -> Res.string.chat_error_timeout
        ChatErrorCategory.SERVER -> Res.string.chat_error_server
        ChatErrorCategory.QUOTA -> Res.string.chat_quota_exceeded
        ChatErrorCategory.BAD_REQUEST -> Res.string.chat_error_bad_request
        ChatErrorCategory.UNKNOWN -> Res.string.chat_error_message
    }
}

@Preview(showBackground = true)
@Composable
private fun MessageItemPreview() {
    MaterialTheme {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MessageItem(
                ChatMessage(1, Role.USER, "帮我用 Kotlin 写一个快速排序"),
                onRetry = {},
            )
            MessageItem(
                ChatMessage(
                    2, Role.ASSISTANT,
                    "好的，下面是 **快速排序** 实现：\n\n" +
                        "```kotlin\nfun quickSort(list: List<Int>): List<Int> {\n" +
                        "    if (list.size <= 1) return list\n    val pivot = list.first()\n" +
                        "    val rest = list.drop(1)\n    return quickSort(rest.filter { it < pivot }) +\n" +
                        "        pivot + quickSort(rest.filter { it >= pivot })\n}\n```\n\n" +
                        "- 平均时间复杂度 `O(n log n)`\n- 最坏 `O(n²)`",
                ),
                onRetry = {},
            )
        }
    }
}


/** 引用来源行：可点击 chip 流式排布（点击经全局 UriHandler 统一出口打开） */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourcesRow(sources: List<SourceRef>) {
    val uriHandler = LocalUriHandler.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        sources.forEach { source ->
            SuggestionChip(
                // 全局 UriHandler 已含「无浏览器 → 应用内 WebView」兜底，此处仅防极端 URL 异常；
                // 失败留日志便于排查，不打扰用户（Sourcery 建议采纳日志、toast 评估后不做）
                onClick = {
                    runCatching { uriHandler.openUri(source.url) }
                        .onFailure { logWarn("SourcesRow", "open source failed: ${'$'}{source.url}", it) }
                },
                label = {
                    Text(source.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
            )
        }
    }
}
