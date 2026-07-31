package whl.trending.chat.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
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
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.filled.Check
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
import java.util.Locale
import kotlinx.coroutines.launch
import whl.trending.ai.auth.AuthState
import whl.trending.ai.auth.globalAuthManager
import whl.trending.ai.core.platform.trackEvent
import whl.trending.chat.ChatViewModel
import whl.trending.chat.R
import whl.trending.chat.attach.ChatImages

/**
 * 系统语音识别契约：输入 BCP-47 语言标签，输出识别文本；取消或识别为空返回 null。
 *
 * 录音发生在识别应用进程内，本 app **不需要 RECORD_AUDIO 权限**——与拍照走
 * TakePicture 同一思路（把设备能力交给系统应用，我们只取结果）。
 */
private class RecognizeSpeech : ActivityResultContract<String, String?>() {
    override fun createIntent(context: Context, input: String): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            // FREE_FORM = 自由口语听写；WEB_SEARCH 偏短查询词，对话场景不合适
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, input)
            putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.chat_voice_prompt))
            // 只取置信度最高的一条：识别错了在输入框改比让用户挑候选更快
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

    override fun parseResult(resultCode: Int, intent: Intent?): String? {
        if (resultCode != Activity.RESULT_OK) return null
        return intent?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }
}

/** 埋点用长度分桶：避免上报原文，也避免高基数维度 */
private fun lengthBucket(chars: Int): String = when {
    chars <= 10 -> "0-10"
    chars <= 30 -> "11-30"
    chars <= 60 -> "31-60"
    else -> "60+"
}

/**
 * 本机是否有能处理语音识别 Intent 的应用。
 *
 * 只判「有没有」，不追究「是哪个」——归因需要的目标包名、权限判据、失败分流曾经都做过，
 * 换来的是连续三轮回归，而收益（引导用户去修第三方应用的权限）低于「直接改用键盘上的
 * 麦克风键」这条本来就存在的路径。尝试性功能不值得背那套复杂度。
 *
 * Android 11+ 包可见性下该查询依赖 manifest 的 `<queries>` 声明，漏掉会在正常设备上也
 * 返回 false（按钮永远不出现）。
 */
private fun hasSpeechRecognizer(context: Context): Boolean = runCatching {
    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).resolveActivity(context.packageManager) != null
}.getOrDefault(false)

/**
 * 底部输入区：图片入口（拍照/相册）+ 待发缩略图条 + 输入框 + 语音/发送按钮。
 *
 * 图片理解仅对登录用户开放：未登录点「+」弹登录引导（服务端另有 403 真闸）。
 * 选图走系统契约（Photo Picker / TakePicture），Android 13+ 全程零运行时权限。
 *
 * 语音输入走系统识别 Intent（[RecognizeSpeech]）：零权限、零服务端成本，因此对匿名
 * 用户直接开放，不设登录闸。设备无识别应用时按钮不渲染（见 speechAvailable）。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChatInputBar(
    input: String,
    canSend: Boolean,
    pendingImages: List<String>,
    searchActive: Boolean = false,
    researchActive: Boolean = false,
    onToggleSearch: () -> Unit = {},
    onToggleResearch: () -> Unit = {},
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
    val voiceFailedText = stringResource(R.string.chat_voice_failed)
    val remaining = ChatViewModel.MAX_IMAGES_PER_MESSAGE - pendingImages.size - processingCount

    val speechAvailable = remember(context) { hasSpeechRecognizer(context) }

    val speechLauncher = rememberLauncherForActivityResult(RecognizeSpeech()) { text ->
        // 空结果（用户取消 / 识别失败）不做任何处理：Intent 契约不回传原因，我们分不清是哪种，
        // 而两者需要的反馈恰好相反（取消要静默、失败要提示）。取消是更常见的那个，故静默。
        // 完成率 chat_voice_result / chat_voice_start 会把问题暴露出来，需要深究时再说。
        if (text == null) return@rememberLauncherForActivityResult
        trackEvent("chat_voice_result", mapOf("chars" to lengthBucket(text.length)))
        // 追加而非覆盖：用户可能已手打半句再改用语音
        onInputChange(if (input.isBlank()) text else "${input.trimEnd()} $text")
    }

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
                // + 菜单常开：搜索 toggle 对匿名可用；图片两项在点击时才做登录闸
                run {
                    Box {
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
                        DropdownMenu(
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
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.chat_input_hint)) },
                    maxLines = 5,
                )
                // 语音与发送互斥占同一位置（微信/ChatGPT 同款），避免底栏挤成一排图标：
                // 无内容可发时给语音入口，一旦有文本或待发图立刻切回发送键。
                // isSending 时不挡语音——用户完全可以在 AI 回复期间先把下一句说好。
                if (speechAvailable && input.isBlank() && pendingImages.isEmpty()) {
                    IconButton(
                        onClick = {
                            trackEvent("chat_voice_start")
                            // 识别语言跟随系统，不跟界面语言设置——界面用英文的人说的未必是英文。
                            // 识别应用被禁用等极端情况：launch 会抛 ActivityNotFoundException
                            runCatching { speechLauncher.launch(Locale.getDefault().toLanguageTag()) }
                                .onFailure {
                                    Toast.makeText(context, voiceFailedText, Toast.LENGTH_SHORT).show()
                                }
                        },
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Mic,
                            contentDescription = stringResource(R.string.chat_voice_input),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    IconButton(
                        onClick = onSend,
                        enabled = canSend,
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(R.string.chat_send),
                            tint = if (canSend) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                        )
                    }
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
