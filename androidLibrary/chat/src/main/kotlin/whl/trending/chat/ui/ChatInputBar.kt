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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
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
import whl.trending.ai.ui.common.TrendingDropdownMenu
import whl.trending.chat.ChatViewModel
import whl.trending.chat.R
import whl.trending.chat.attach.ChatImages

/**
 * 底部输入区：图片入口（拍照/相册）+ 待发缩略图条 + 输入框 + 发送按钮。
 *
 * 样式参照 EchoFlow 的 composer（`ChatComposer.kt` 的 `InputToolbar`）：输入区是一枚**悬浮胶囊**
 * 而非贴底通栏——大圆角 + `surfaceContainerHigh` + 投影，里面的 TextField 去掉容器和指示线，
 * 边框感由胶囊本身承担。
 *
 * 两端按钮没照抄 EchoFlow：它给「+」和发送都配了
 * [androidx.compose.material3.MaterialShapes] 异形填充容器（Cookie/Sunny + 按下形变）。我们的取舍是
 * **整条只留一个彩色重心**，放在右侧的发送键上——「+」退成无容器裸图标，与 ChatGPT / Gemini 的
 * composer 一致；发送键用圆形 primary 实心，不用异形，与 app 其余部分的全圆形语言一致。
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
    searchActive: Boolean = false,
    researchActive: Boolean = false,
    onToggleSearch: () -> Unit = {},
    onToggleResearch: () -> Unit = {},
    autoFocus: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 进入页面自动聚焦输入框，键盘随焦点自动弹出（官方做法：focusRequester + 在组合外 requestFocus）
    val inputFocusRequester = remember { FocusRequester() }
    LaunchedEffect(autoFocus) {
        if (autoFocus) inputFocusRequester.requestFocus()
    }
    val authState by globalAuthManager.authState.collectAsState()

    var menuExpanded by remember { mutableStateOf(false) }
    var showLoginDialog by remember { mutableStateOf(false) }
    var processingCount by remember { mutableIntStateOf(0) }
    var captureTarget by remember { mutableStateOf<Pair<Uri, File>?>(null) }

    val failedText = stringResource(R.string.chat_image_processing_failed)
    val remaining = ChatViewModel.MAX_IMAGES_PER_MESSAGE - pendingImages.size - processingCount

    fun ingest(uri: Uri, deleteAfter: File? = null) {
        processingCount++
        scope.launch {
            val path = ChatImages.ingest(context, uri)
            deleteAfter?.delete()
            processingCount--
            if (path != null) {
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
        uris.take(remaining.coerceAtLeast(0)).forEach { ingest(it) }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        val target = captureTarget
        captureTarget = null
        if (target != null) {
            if (success) {
                ingest(Uri.fromFile(target.second), deleteAfter = target.second)
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
                    globalAuthManager.signIn("chat_image_dialog")
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

    Column(
        // 避让手势导航条与键盘（union 取两者较大值，避免双重叠加）。
        // 胶囊化后底部不再有铺到屏幕边的背景板，插入的这段留白就是胶囊与屏幕底边的距离
        modifier = modifier.fillMaxWidth()
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
        Surface(
            // 固定 36dp 而不是 EchoFlow 的 CircleShape：单行时胶囊高 72dp（TextField 最小高 56 + 上下
            // 各 8 的内边距），36dp 恰好等于半高，与全圆一模一样；但 CircleShape 的半径跟着高度走，
            // 文字换到第 3、4 行后两端会撑成夸张的椭圆，把两侧按钮挤向中间
            shape = RoundedCornerShape(36.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // + 菜单常开：搜索 toggle 对匿名可用；图片两项在点击时才做登录闸
                run {
                    Box {
                        // 无容器裸图标：附件/能力开关是次要入口，给它填充容器就等于在输入区里
                        // 造出第二个彩色重心，视线一进来先被拉到左下角。ChatGPT 与 Gemini 的
                        // composer 都是这么处理的——左侧零视觉重量，色彩预算全留给右侧那一个主操作
                        IconButton(
                            onClick = { menuExpanded = true },
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
                        TrendingDropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            // 能力开关：联网搜索（勾选态 = 已开启；EchoFlow 的「菜单开启 + chip 回显」范式）
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.chat_web_search)) },
                                leadingIcon = { Icon(Icons.Outlined.TravelExplore, contentDescription = null) },
                                trailingIcon = {
                                    if (searchActive) Icon(Icons.Filled.Check, contentDescription = null)
                                },
                                onClick = {
                                    menuExpanded = false
                                    onToggleSearch()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.chat_deep_research)) },
                                leadingIcon = { Icon(Icons.Outlined.Science, contentDescription = null) },
                                trailingIcon = {
                                    if (researchActive) Icon(Icons.Filled.Check, contentDescription = null)
                                },
                                onClick = {
                                    menuExpanded = false
                                    onToggleResearch()
                                },
                            )
                            if (globalAuthManager.isSupported) DropdownMenuItem(
                                text = { Text(stringResource(R.string.chat_attach_camera)) },
                                leadingIcon = { Icon(Icons.Outlined.PhotoCamera, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    if (authState !is AuthState.LoggedIn) {
                                        showLoginDialog = true
                                        return@DropdownMenuItem
                                    }
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
                            if (globalAuthManager.isSupported) DropdownMenuItem(
                                text = { Text(stringResource(R.string.chat_attach_album)) },
                                leadingIcon = { Icon(Icons.Outlined.Image, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    if (authState !is AuthState.LoggedIn) {
                                        showLoginDialog = true
                                        return@DropdownMenuItem
                                    }
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
                TextField(
                    value = input,
                    onValueChange = onInputChange,
                    // focusRequester 必须声明在可聚焦项之前才会关联（官方文档「焦点修饰符的优先级」）
                    modifier = Modifier.focusRequester(inputFocusRequester).weight(1f),
                    placeholder = { Text(stringResource(R.string.chat_input_hint)) },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    // 容器与指示线全部透明：外层胶囊已经是这块区域的视觉容器，
                    // 再叠一层 TextField 自己的底色/下划线就成了「框中框」
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                    maxLines = 5,
                )
                FilledIconButton(
                    onClick = onSend,
                    enabled = canSend,
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.chat_send),
                        modifier = Modifier.size(20.dp),
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
