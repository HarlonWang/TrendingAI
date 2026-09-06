package whl.trending.chat.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import okio.Path.Companion.toPath
import whl.trending.chat.attach.VoiceRecording
import whl.trending.chat.attach.VoiceStart
import whl.trending.chat.attach.rememberChatImagePicker
import whl.trending.chat.attach.rememberChatVoiceRecorder
import whl.trending.chat.host.ChatVoiceOutcome
import whl.trending.chat.host.chatHost
import whl.trending.chat.ChatViewModel
import trendingai.chat.generated.resources.Res
import trendingai.chat.generated.resources.chat_attach
import trendingai.chat.generated.resources.chat_attach_album
import trendingai.chat.generated.resources.chat_attach_camera
import trendingai.chat.generated.resources.chat_image_login_confirm
import trendingai.chat.generated.resources.chat_image_login_dismiss
import trendingai.chat.generated.resources.chat_image_login_message
import trendingai.chat.generated.resources.chat_image_login_title
import trendingai.chat.generated.resources.chat_image_remove
import trendingai.chat.generated.resources.chat_input_hint
import trendingai.chat.generated.resources.chat_model_unlock_dismiss
import trendingai.chat.generated.resources.chat_send
import trendingai.chat.generated.resources.chat_voice_failed
import trendingai.chat.generated.resources.chat_voice_mic
import trendingai.chat.generated.resources.chat_voice_permission_message
import trendingai.chat.generated.resources.chat_voice_permission_settings
import trendingai.chat.generated.resources.chat_voice_permission_title
import trendingai.chat.generated.resources.chat_voice_pro_message
import trendingai.chat.generated.resources.chat_voice_pro_title
import trendingai.chat.generated.resources.chat_voice_release_cancel
import trendingai.chat.generated.resources.chat_voice_release_send
import trendingai.chat.generated.resources.chat_voice_transcribing
import trendingai.chat.generated.resources.chat_user_image
import trendingai.chat.generated.resources.chat_web_search

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
 *
 * 语音录入：输入框为空时右侧主按钮是麦克风（有文字即变回发送键，不加第三个图标）。
 * 按住说话、松手即发、上滑取消；转写成文本后直接发送，不经输入框。仅 Pro 可用，
 * 非 Pro 按下弹纯告知弹窗（与锁定模型同一处理，不外跳）。
 *
 * @param voiceEnabled 宿主是否注入了转写能力；false 时永远显示发送键
 * @param isTranscribing 转写在途：麦克风位显示 loading，输入框占位改为「正在识别」
 * @param onVoiceRecorded 一段合格的录音就绪（松手或触顶）
 * @param onVoiceOutcome 转写前就结束的语音结果（取消 / 太短 / 权限 / Pro 闸），供埋点
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
    onToggleSearch: () -> Unit = {},
    autoFocus: Boolean = false,
    voiceEnabled: Boolean = false,
    isTranscribing: Boolean = false,
    voiceMaxDurationMs: Int = 60_000,
    onVoiceRecorded: (VoiceRecording) -> Unit = {},
    onVoiceOutcome: (ChatVoiceOutcome, Long?) -> Unit = { _, _ -> },
) {
    // 进入页面自动聚焦输入框，键盘随焦点自动弹出（官方做法：focusRequester + 在组合外 requestFocus）
    val inputFocusRequester = remember { FocusRequester() }
    LaunchedEffect(autoFocus) {
        if (autoFocus) inputFocusRequester.requestFocus()
    }
    val loggedIn by chatHost.isLoggedIn.collectAsState(chatHost.isLoggedInNow())

    var menuExpanded by remember { mutableStateOf(false) }
    var showLoginDialog by remember { mutableStateOf(false) }
    var processingCount by remember { mutableIntStateOf(0) }

    val maxImages = ChatViewModel.maxImagesPerMessage()
    val remaining = maxImages - pendingImages.size - processingCount
    val picker = rememberChatImagePicker(
        maxImages = maxImages,
        remaining = { maxImages - pendingImages.size - processingCount },
        onProcessingChange = { processingCount += it },
        onImageReady = onAddImage,
    )

    val isPro by chatHost.isPro.collectAsState(chatHost.currentIsPro())
    var showProDialog by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    // 录音中：startedAt > 0；inCancelZone 随手指上滑切换
    var recordingStartedAt by remember { mutableLongStateOf(0L) }
    var inCancelZone by remember { mutableStateOf(false) }
    val recorder = rememberChatVoiceRecorder(
        maxDurationMs = voiceMaxDurationMs,
        minDurationMs = MIN_VOICE_DURATION_MS,
        onAutoStop = { recording ->
            // 手指仍按着：先收掉录音态，后续抬手在手势里被 startedAt==0 挡掉
            recordingStartedAt = 0L
            onVoiceRecorded(recording)
        },
        onPermissionDenied = {
            showPermissionDialog = true
            onVoiceOutcome(ChatVoiceOutcome.PERMISSION_DENIED, null)
        },
    )
    // 录音中离开页面（返回键、Activity 重建）：手势协程随组合一起没了，录音器得跟着停
    DisposableEffect(recorder) {
        onDispose { recorder.cancel() }
    }
    val showMic = voiceEnabled && recorder.isAvailable && input.isBlank() && pendingImages.isEmpty()
    val haptic = LocalHapticFeedback.current
    val showNotice = rememberShowNotice()
    val voiceFailedText = stringResource(Res.string.chat_voice_failed)

    if (showProDialog) {
        AlertDialog(
            onDismissRequest = { showProDialog = false },
            title = { Text(stringResource(Res.string.chat_voice_pro_title)) },
            text = { Text(stringResource(Res.string.chat_voice_pro_message)) },
            confirmButton = {
                TextButton(onClick = { showProDialog = false }) {
                    Text(stringResource(Res.string.chat_model_unlock_dismiss))
                }
            },
        )
    }
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text(stringResource(Res.string.chat_voice_permission_title)) },
            text = { Text(stringResource(Res.string.chat_voice_permission_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionDialog = false
                    recorder.openPermissionSettings()
                }) {
                    Text(stringResource(Res.string.chat_voice_permission_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text(stringResource(Res.string.chat_image_login_dismiss))
                }
            },
        )
    }

    if (showLoginDialog) {
        AlertDialog(
            onDismissRequest = { showLoginDialog = false },
            title = { Text(stringResource(Res.string.chat_image_login_title)) },
            text = { Text(stringResource(Res.string.chat_image_login_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showLoginDialog = false
                    chatHost.signIn("chat_image_dialog")
                }) {
                    Text(stringResource(Res.string.chat_image_login_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLoginDialog = false }) {
                    Text(stringResource(Res.string.chat_image_login_dismiss))
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
                if (recordingStartedAt > 0L) {
                    RecordingStatus(
                        startedAt = recordingStartedAt,
                        inCancelZone = inCancelZone,
                        modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                    )
                } else {
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
                                contentDescription = stringResource(Res.string.chat_attach),
                                tint = if (remaining > 0) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.outline
                                },
                            )
                        }
                        ChatDropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            // 能力开关：联网搜索（勾选态 = 已开启；EchoFlow 的「菜单开启 + chip 回显」范式）
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.chat_web_search)) },
                                leadingIcon = { Icon(Icons.Outlined.TravelExplore, contentDescription = null) },
                                trailingIcon = {
                                    if (searchActive) Icon(Icons.Filled.Check, contentDescription = null)
                                },
                                onClick = {
                                    menuExpanded = false
                                    onToggleSearch()
                                },
                            )
                            if (chatHost.canSignIn && picker.canCapture) DropdownMenuItem(
                                text = { Text(stringResource(Res.string.chat_attach_camera)) },
                                leadingIcon = { Icon(Icons.Outlined.PhotoCamera, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    if (!loggedIn) {
                                        showLoginDialog = true
                                        return@DropdownMenuItem
                                    }
                                    picker.capture()
                                },
                            )
                            if (chatHost.canSignIn) DropdownMenuItem(
                                text = { Text(stringResource(Res.string.chat_attach_album)) },
                                leadingIcon = { Icon(Icons.Outlined.Image, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    if (!loggedIn) {
                                        showLoginDialog = true
                                        return@DropdownMenuItem
                                    }
                                    picker.pickFromAlbum()
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
                    enabled = !isTranscribing,
                    placeholder = {
                        Text(
                            stringResource(
                                if (isTranscribing) Res.string.chat_voice_transcribing else Res.string.chat_input_hint,
                            ),
                        )
                    },
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
                }
                if (showMic || recordingStartedAt > 0L) {
                    val cancelThresholdPx = with(LocalDensity.current) { CANCEL_SLIDE_THRESHOLD.toPx() }
                    // 按住/上滑/抬手自行处理：FilledIconButton 的 onClick 表达不了「按下开始、抬手结束」
                    MicButton(
                        active = recordingStartedAt > 0L,
                        cancelZone = inCancelZone,
                        loading = isTranscribing,
                        modifier = Modifier.pointerInput(isPro, isTranscribing) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                down.consume()
                                if (isTranscribing) return@awaitEachGesture
                                if (!isPro) {
                                    showProDialog = true
                                    onVoiceOutcome(ChatVoiceOutcome.PRO_GATE, null)
                                    return@awaitEachGesture
                                }
                                when (recorder.start()) {
                                    VoiceStart.PERMISSION_PENDING -> return@awaitEachGesture
                                    VoiceStart.FAILED -> {
                                        showNotice(voiceFailedText)
                                        onVoiceOutcome(ChatVoiceOutcome.ERROR, null)
                                        return@awaitEachGesture
                                    }
                                    VoiceStart.STARTED -> Unit
                                }
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                recordingStartedAt = epochNow()
                                inCancelZone = false
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                    if (change == null || !change.pressed) {
                                        // 触顶自动停止已取件时 startedAt 归零，这里的抬手不再重复取件
                                        if (recordingStartedAt > 0L) {
                                            val elapsed = epochNow() - recordingStartedAt
                                            recordingStartedAt = 0L
                                            if (change == null || inCancelZone) {
                                                recorder.cancel()
                                                onVoiceOutcome(ChatVoiceOutcome.CANCELLED, elapsed)
                                            } else {
                                                val recording = recorder.stop()
                                                if (recording != null) onVoiceRecorded(recording)
                                                else onVoiceOutcome(ChatVoiceOutcome.TOO_SHORT, elapsed)
                                            }
                                        }
                                        break
                                    }
                                    inCancelZone = change.position.y < -cancelThresholdPx
                                    change.consume()
                                }
                            }
                        },
                    )
                } else {
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
                        contentDescription = stringResource(Res.string.chat_send),
                        modifier = Modifier.size(20.dp),
                    )
                }
                }
            }
        }
    }
}

private const val MIN_VOICE_DURATION_MS = 1_000
private val CANCEL_SLIDE_THRESHOLD = 80.dp

private fun epochNow(): Long = whl.trending.chat.core.epochMillis()

/** 麦克风主按钮：与发送键同位同尺寸；录音中放大一档、进入取消区换 error 色。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MicButton(
    active: Boolean,
    cancelZone: Boolean,
    loading: Boolean,
    modifier: Modifier = Modifier,
) {
    val container = when {
        active && cancelZone -> MaterialTheme.colorScheme.error
        active -> MaterialTheme.colorScheme.primary
        loading -> MaterialTheme.colorScheme.surfaceContainerHighest
        else -> MaterialTheme.colorScheme.primary
    }
    val content = when {
        active && cancelZone -> MaterialTheme.colorScheme.onError
        loading -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onPrimary
    }
    Surface(
        shape = CircleShape,
        color = container,
        contentColor = content,
        modifier = modifier.size(if (active) 56.dp else 48.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (loading) {
                LoadingIndicator(modifier = Modifier.size(24.dp))
            } else {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = stringResource(Res.string.chat_voice_mic),
                    modifier = Modifier.size(if (active) 24.dp else 20.dp),
                )
            }
        }
    }
}

/** 录音中占据输入区的状态：计时 + 操作提示（进入取消区时换文案）。 */
@Composable
private fun RecordingStatus(
    startedAt: Long,
    inCancelZone: Boolean,
    modifier: Modifier = Modifier,
) {
    var elapsedMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(startedAt) {
        while (true) {
            elapsedMs = epochNow() - startedAt
            delay(200)
        }
    }
    val seconds = elapsedMs / 1000
    Column(modifier = modifier) {
        Text(
            text = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}",
            style = MaterialTheme.typography.titleMedium,
            color = if (inCancelZone) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(
                if (inCancelZone) Res.string.chat_voice_release_cancel else Res.string.chat_voice_release_send,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
                    model = path.toPath(),
                    contentDescription = stringResource(Res.string.chat_user_image),
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
                            contentDescription = stringResource(Res.string.chat_image_remove),
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
