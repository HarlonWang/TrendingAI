package whl.trending.chat.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import whl.trending.ai.data.local.AppLanguage
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.chat.ChatViewModel
import whl.trending.chat.R
import whl.trending.chat.attach.ChatImages

/**
 * 系统语音识别契约：输入 BCP-47 语言标签，**null 表示不指定**（识别应用自行跟随系统）；
 * 输出识别文本，用户取消或识别为空返回 null。
 *
 * 录音发生在识别应用进程内，本 app **不需要 RECORD_AUDIO 权限**——与拍照走
 * TakePicture 同一思路（把设备能力交给系统应用，我们只取结果）。
 */
private class RecognizeSpeech : ActivityResultContract<String?, String?>() {
    override fun createIntent(context: Context, input: String?): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            // FREE_FORM = 自由口语听写；WEB_SEARCH 偏短查询词，对话场景不合适
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            // 只在用户显式选过界面语言时才指定；跟随系统时**必须不传**——
            // 传了等于把日语/德语用户钉死在英文引擎上，比默认行为更糟
            input?.let { putExtra(RecognizerIntent.EXTRA_LANGUAGE, it) }
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
 * 设备上处理 ACTION_RECOGNIZE_SPEECH 的应用。
 *
 * [pkg] 为 null 表示「有识别能力，但无法确定是哪个应用」——多个引擎注册且用户未设默认时，
 * 系统会弹选择器（`resolveActivity` 此时返回 ResolverActivity，包名 "android"，拿它去查
 * 权限/跳设置全是错的）。这种情况下按钮照常显示，只是放弃基于包的权限判据与引导。
 */
private data class Recognizer(val pkg: String?, val label: String?)

/** ResolverActivity 所属包：系统选择器，不是真正的识别应用 */
private const val ANDROID_RESOLVER_PKG = "android"

/**
 * 「本机没有识别应用」进程内只上报一次的标记。
 *
 * 这类设备上麦克风按钮根本不渲染，永远不会有 chat_voice_start，不记这一笔就完全看不见
 * 它们的存在。每次进 chat 都记会把该维度刷爆，故按进程生命周期去重。
 */
private var unsupportedReported = false

/**
 * 权限受阻的连续次数，进程内累计。
 *
 * 刻意不用 remember/rememberSaveable：那样只覆盖单次 composition，退出 chat 再进来就归零，
 * 「第二次才弹引导」的节流形同虚设。进程级计数才是用户感知里的「又失败了一次」。
 * 识别成功后清零，避免历史失败在很久之后突然把弹窗推出来。
 */
private var voiceBlockedCount = 0

private fun findRecognizer(context: Context): Recognizer? {
    val pm = context.packageManager
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
    val resolved = intent.resolveActivity(pm) ?: return null
    // 命中系统选择器说明装了多个引擎且没设默认：有能力，但目标包不确定
    if (resolved.packageName == ANDROID_RESOLVER_PKG) {
        val candidates = pm.queryIntentActivities(intent, 0)
        // 选择器只有唯一候选时仍可确定目标（理论上不该出现，防御性处理）
        val only = candidates.singleOrNull()?.activityInfo?.packageName
            ?: return Recognizer(pkg = null, label = null)
        return Recognizer(only, labelOf(pm, only))
    }
    return Recognizer(resolved.packageName, labelOf(pm, resolved.packageName))
}

private fun labelOf(pm: PackageManager, pkg: String): String = runCatching {
    pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
}.getOrDefault(pkg)

/**
 * 识别应用是否已拿到录音权限。
 *
 * 只能用作**事后**判据：识别返回空时用它区分「用户主动取消」与「授权链没走通」——
 * `ACTION_RECOGNIZE_SPEECH` 两种情况都回 RESULT_CANCELED，靠耗时区分不可靠
 * （小米的失败路径是弹框等用户点确定，耗时与正常说话完全重叠，2026-07-31 实测）。
 *
 * 不可前置拦截：多数识别应用的录音权限是首次进入时自己运行时申请的，未授权是正常
 * 初始态，提前拦会把正常首启也堵死。
 */
private fun hasRecordAudio(context: Context, pkg: String): Boolean =
    context.packageManager.checkPermission(android.Manifest.permission.RECORD_AUDIO, pkg) ==
        PackageManager.PERMISSION_GRANTED

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
    val voiceBlockedText = stringResource(R.string.chat_voice_blocked_toast)
    val remaining = ChatViewModel.MAX_IMAGES_PER_MESSAGE - pendingImages.size - processingCount

    // 设备上的语音识别应用。Android 11+ 包可见性下，该查询依赖 manifest 里 <queries>
    // 对 android.speech.action.RECOGNIZE_SPEECH 的声明，漏掉声明会在正常设备上也返回
    // null（按钮永远不出现）。
    val recognizer = remember(context) { findRecognizer(context) }
    var showVoiceGuide by rememberSaveable { mutableStateOf(false) }

    // 无识别应用的设备不会产生任何 chat_voice_* 事件，单独记一笔才有分母
    LaunchedEffect(recognizer) {
        if (recognizer == null && !unsupportedReported) {
            unsupportedReported = true
            trackEvent("chat_voice_unsupported")
        }
    }
    // 识别语言只在用户**显式**选过界面语言时才指定，FOLLOW_SYSTEM 传 null 由识别应用
    // 自行跟随系统。不能套用 ChatApi.resolveLang() 的 zh/en 二选一口径——那是后端只支持
    // 两种摘要语言所致，识别引擎支持的语言远不止两种，钉成 en-US 会让日语/德语用户
    // 被迫用英文引擎识别母语，比不传这个 extra 更糟。
    val appLanguage by globalSettingsManager.appLanguage.collectAsState(AppLanguage.FOLLOW_SYSTEM)
    val voiceLocale = remember(appLanguage) {
        when (appLanguage) {
            AppLanguage.CHINESE -> "zh-CN"
            AppLanguage.ENGLISH -> "en-US"
            AppLanguage.FOLLOW_SYSTEM -> null
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(RecognizeSpeech()) { text ->
        if (text != null) {
            trackEvent("chat_voice_result", mapOf("chars" to lengthBucket(text.length)))
            voiceBlockedCount = 0 // 成功即清零：授权链已通，历史失败不该再攒着推弹窗
            // 追加而非覆盖：用户可能已手打半句再改用语音
            onInputChange(if (input.isBlank()) text else "${input.trimEnd()} $text")
            return@rememberLauncherForActivityResult
        }
        // 空结果分流。识别应用**有**录音权限 → 排除了权限原因，静默（多数是用户主动取消，
        // 但也混有引擎侧失败：离线、语言包缺失、内部错误——Intent 契约不回传原因，我们无从
        // 区分，故事件名只描述现象，不当纯取消率用，见 spec 的埋点小节）。
        // **无**录音权限 → 授权链没走通，给引导。
        val pkg = recognizer?.pkg
        if (pkg == null || hasRecordAudio(context, pkg)) {
            trackEvent("chat_voice_empty")
        } else {
            trackEvent("chat_voice_blocked", mapOf("pkg" to pkg, "attempt" to voiceBlockedCount.toString()))
            voiceBlockedCount++
            // 首次失败只给 Toast：用户第一次点开、在识别应用的协议/授权页按返回是很正常的
            // 「我再想想」，此时权限确实还没授，但立刻弹模态框既打扰又显得急躁。
            // 连续第二次才升级为带「去设置」的引导——两次都失败基本可以排除「只是随手取消」。
            if (voiceBlockedCount >= 2) {
                showVoiceGuide = true
            } else {
                Toast.makeText(context, voiceBlockedText, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // pkg/label 在 blocked 分支必然非空（判据本身要求确定的目标包），这里再取一次是为了
    // 让编译器看到非空，也顺带兜住「多引擎未设默认」时不该弹引导的情况
    val guideTarget = recognizer?.pkg?.let { p -> recognizer.label?.let { l -> p to l } }
    if (showVoiceGuide && guideTarget != null) {
        val (guidePkg, guideLabel) = guideTarget
        AlertDialog(
            onDismissRequest = { showVoiceGuide = false },
            title = { Text(stringResource(R.string.chat_voice_guide_title)) },
            text = { Text(stringResource(R.string.chat_voice_guide_message, guideLabel)) },
            confirmButton = {
                TextButton(onClick = {
                    showVoiceGuide = false
                    trackEvent("chat_voice_guide_settings_click")
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", guidePkg, null),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    // 极少数 ROM 不提供应用详情页：兜底 Toast，用户仍可走键盘语音
                    runCatching { context.startActivity(intent) }
                        .onFailure {
                            Toast.makeText(context, voiceFailedText, Toast.LENGTH_SHORT).show()
                        }
                }) {
                    Text(stringResource(R.string.chat_voice_guide_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { showVoiceGuide = false }) {
                    Text(stringResource(R.string.chat_voice_guide_dismiss))
                }
            },
        )
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
                if (recognizer != null && input.isBlank() && pendingImages.isEmpty()) {
                    IconButton(
                        onClick = {
                            trackEvent("chat_voice_start")
                            // 识别应用被禁用等极端情况：launch 会抛 ActivityNotFoundException。
                            // 补一个配对的终止事件——start 已经记了，不补的话这次点击会永远
                            // 挂在完成率的分母上，把指标压低且无从解释。
                            runCatching { speechLauncher.launch(voiceLocale) }
                                .onFailure {
                                    trackEvent("chat_voice_launch_failed")
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
