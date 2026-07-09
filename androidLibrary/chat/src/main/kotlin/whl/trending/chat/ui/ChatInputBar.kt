package whl.trending.chat.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.io.File
import kotlinx.coroutines.launch
import whl.trending.ai.auth.AuthState
import whl.trending.ai.auth.globalAuthManager
import whl.trending.ai.core.platform.trackEvent
import whl.trending.chat.ChatViewModel
import whl.trending.chat.R
import whl.trending.chat.attach.ChatImages

/**
 * 底部输入区：图片入口（拍照/相册）+ 待发缩略图条 + 输入框 + 发送按钮。
 *
 * 图片理解仅对登录用户开放：未登录点「+」弹登录引导（服务端另有 403 真闸）。
 * 选图走系统契约（Photo Picker / TakePicture），Android 13+ 全程零运行时权限。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChatInputBar(
    input: String,
    canSend: Boolean,
    pendingImages: List<String>,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onAddImage: (String) -> Unit,
    onRemoveImage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authState by globalAuthManager.authState.collectAsState()

    var menuExpanded by remember { mutableStateOf(false) }
    var showLoginDialog by remember { mutableStateOf(false) }
    var processingCount by remember { mutableIntStateOf(0) }
    var captureTarget by remember { mutableStateOf<Pair<Uri, File>?>(null) }

    val failedText = stringResource(R.string.chat_image_processing_failed)
    val remaining = ChatViewModel.MAX_IMAGES_PER_MESSAGE - pendingImages.size - processingCount

    fun ingest(uri: Uri, source: String, deleteAfter: File? = null) {
        processingCount++
        scope.launch {
            val path = ChatImages.ingest(context, uri)
            deleteAfter?.delete()
            processingCount--
            if (path != null) {
                trackEvent("chat_image_add", mapOf("source" to source))
                onAddImage(path)
            } else {
                Toast.makeText(context, failedText, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val albumLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(ChatViewModel.MAX_IMAGES_PER_MESSAGE),
    ) { uris ->
        // 选择器允许选满上限，剩余名额不足时截断
        uris.take(remaining.coerceAtLeast(0)).forEach { ingest(it, source = "album") }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        val target = captureTarget
        captureTarget = null
        if (target != null) {
            if (success) {
                ingest(Uri.fromFile(target.second), source = "camera", deleteAfter = target.second)
            } else {
                target.second.delete()
            }
        }
    }

    if (showLoginDialog) {
        AlertDialog(
            onDismissRequest = { showLoginDialog = false },
            title = { Text(stringResource(R.string.chat_image_login_title)) },
            text = { Text(stringResource(R.string.chat_image_login_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showLoginDialog = false
                    trackEvent("chat_image_login_click")
                    globalAuthManager.signIn()
                }) {
                    Text(stringResource(R.string.chat_image_login_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLoginDialog = false }) {
                    Text(stringResource(R.string.chat_image_login_dismiss))
                }
            },
        )
    }

    Surface(tonalElevation = 3.dp, modifier = modifier.fillMaxWidth()) {
        Column(
            // 避让手势导航条与键盘（union 取两者较大值，避免双重叠加）；
            // Surface 背景仍铺到屏幕底边，仅内容上移
            modifier = Modifier.fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            if (pendingImages.isNotEmpty() || processingCount > 0) {
                PendingImageStrip(
                    pendingImages = pendingImages,
                    processingCount = processingCount,
                    onRemoveImage = onRemoveImage,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 登录能力不可用的构建（如无 auth 渠道）直接隐藏图片入口
                if (globalAuthManager.isSupported) {
                    Box {
                        IconButton(
                            onClick = {
                                if (authState !is AuthState.LoggedIn) {
                                    showLoginDialog = true
                                } else {
                                    menuExpanded = true
                                }
                            },
                            enabled = remaining > 0,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(R.string.chat_attach),
                                tint = if (remaining > 0) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.outline
                                },
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.chat_attach_camera)) },
                                leadingIcon = { Icon(Icons.Outlined.PhotoCamera, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    val target = ChatImages.newCaptureTarget(context)
                                    captureTarget = target
                                    // 极少数无相机应用的设备：launch 会抛 ActivityNotFoundException
                                    runCatching { cameraLauncher.launch(target.first) }
                                        .onFailure {
                                            captureTarget = null
                                            Toast.makeText(context, failedText, Toast.LENGTH_SHORT).show()
                                        }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.chat_attach_album)) },
                                leadingIcon = { Icon(Icons.Outlined.Image, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    albumLauncher.launch(
                                        PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly,
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.chat_input_hint)) },
                    maxLines = 5,
                )
                IconButton(
                    onClick = onSend,
                    enabled = canSend,
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.chat_send),
                        tint = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}

/** 待发图片缩略图条：可逐张移除；处理中的名额显示 LoadingIndicator 占位。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PendingImageStrip(
    pendingImages: List<String>,
    processingCount: Int,
    onRemoveImage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(modifier = modifier.fillMaxWidth()) {
        items(pendingImages, key = { it }) { path ->
            Box(modifier = Modifier.padding(end = 8.dp)) {
                AsyncImage(
                    model = File(path),
                    contentDescription = stringResource(R.string.chat_user_image),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
                Surface(
                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f),
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp),
                ) {
                    IconButton(
                        onClick = { onRemoveImage(path) },
                        modifier = Modifier.size(20.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.chat_image_remove),
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
        items(processingCount) {
            Box(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                LoadingIndicator(modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatInputBarPreview() {
    MaterialTheme {
        ChatInputBar(
            input = "你好",
            canSend = true,
            pendingImages = emptyList(),
            onInputChange = {},
            onSend = {},
            onAddImage = {},
            onRemoveImage = {},
        )
    }
}
