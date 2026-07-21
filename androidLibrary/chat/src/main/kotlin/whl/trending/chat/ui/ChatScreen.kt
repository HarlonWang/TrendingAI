package whl.trending.chat.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import whl.trending.ai.chat.ChatContext
import whl.trending.chat.ChatViewModel
import whl.trending.chat.DetailSummaryPolicy
import whl.trending.chat.R
import whl.trending.chat.db.ChatDatabase
import whl.trending.chat.engine.ChatApi
import whl.trending.chat.engine.ChatEngine
import whl.trending.chat.store.ChatStore
import java.io.File

/** 入口键：与 [ChatStore.entryKeyOf] 同源语义，驱动「同一入口只 enterEntry 一次」 */
private fun entryKeyOf(context: ChatContext?): String = ChatStore.entryKeyOf(context)

/**
 * 全屏聊天页。通用入口传 [initialContext] = null；带上下文入口传具体条目。
 *
 * P1 起单 ViewModel（key 固定）+ 会话抽屉：入口进入时 [ChatViewModel.enterEntry]
 * 恢复该入口最近会话（跨进程），抽屉可开新会话/切历史/重命名/删除。
 *
 * @param engine 默认正式引擎 [ChatApi]；Demo 可注入 FakeChatEngine。
 * @param persistent Demo/预览可关（纯内存模式，行为与 P1 前一致）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    initialContext: ChatContext?,
    onBack: () -> Unit,
    engine: ChatEngine = ChatApi.shared,
    initialMessages: List<whl.trending.chat.model.ChatMessage> = emptyList(),
    persistent: Boolean = true,
) {
    val appContext = LocalContext.current.applicationContext
    val store = remember(persistent) {
        if (!persistent) null
        else ChatStore(ChatDatabase.get(appContext), File(appContext.filesDir, "chat_images"))
    }
    val viewModel: ChatViewModel = viewModel(key = "chat") {
        ChatViewModel(engine, initialContext, initialMessages, store)
    }
    val state by viewModel.uiState.collectAsState()
    val threads by viewModel.threads.collectAsState()
    val currentThreadId by viewModel.currentThreadId.collectAsState()

    val entryKey = entryKeyOf(initialContext)
    // 每个屏实例对每个入口只 enter 一次：配置变更（rememberSaveable）不重进，
    // 避免抽屉切走后旋转屏幕又被拽回入口会话
    var enteredKey by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(entryKey) {
        if (enteredKey != entryKey) {
            viewModel.enterEntry(initialContext)
            enteredKey = entryKey
        }
    }

    // README 详情页「一键解读」入口：进入会话后自动触发一次详细解读，省去手动点 chip。
    // chipVisible 已含「GitHub + README 达标 + 尚无成功解读」判定，天然幂等、防重复触发。
    val autoDetailPrompt = stringResource(R.string.chat_action_detail_summary)
    LaunchedEffect(entryKey) {
        if (initialContext?.autoDetailSummary == true &&
            DetailSummaryPolicy.chipVisible(initialContext, viewModel.uiState.value.messages)
        ) {
            viewModel.sendDetailSummary(autoDetailPrompt)
        }
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    fun closeDrawer() = scope.launch { drawerState.close() }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ThreadDrawer(
                threads = threads,
                currentThreadId = currentThreadId,
                onNewThread = {
                    viewModel.startNewThread()
                    closeDrawer()
                },
                onSwitch = { id ->
                    viewModel.switchThread(id)
                    closeDrawer()
                },
                onRename = viewModel::renameThread,
                onDelete = viewModel::deleteThread,
            )
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = initialContext?.title
                                    ?: stringResource(R.string.chat_assistant_title),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Spacer(Modifier.width(8.dp))
                            BetaBadge()
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.chat_back),
                            )
                        }
                    },
                    actions = {
                        // 会话抽屉入口（纯内存模式无历史可言，隐藏）
                        if (store != null) {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(
                                    imageVector = Icons.Filled.Menu,
                                    contentDescription = stringResource(R.string.chat_history),
                                )
                            }
                        }
                    },
                )
            },
            bottomBar = {
                Column {
                    // 「一键详细解读」chip：GitHub 条目 + README 达标 + 尚无成功解读 → 常驻显示
                    // （不同于介绍 chip 的「messages 为空才显示」）；生成中置灰，成功一次后隐藏。
                    if (DetailSummaryPolicy.chipVisible(initialContext, state.messages)) {
                        val detailPrompt = stringResource(R.string.chat_action_detail_summary)
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                            AssistChip(
                                onClick = { viewModel.sendDetailSummary(detailPrompt) },
                                enabled = !state.isSending,
                                label = { Text(detailPrompt) },
                            )
                        }
                    }
                    // 尚无对话时在输入框上方显示入口对应的快捷问（label 资源 to prompt 资源）：
                    // 通用助手入口问能力，README 入口问项目介绍。messages 非空即隐藏，
                    // 天然覆盖"发送后隐藏"与"恢复历史会话不再显示"。
                    val quickAction = when {
                        initialContext == null ->
                            R.string.chat_action_what_can_you_do to R.string.chat_action_what_can_you_do_prompt
                        initialContext.sourceUrl != null ->
                            R.string.chat_action_what_is_this to R.string.chat_action_what_is_this_prompt
                        else -> null
                    }
                    if (quickAction != null && state.messages.isEmpty()) {
                        val prompt = stringResource(quickAction.second)
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                            AssistChip(
                                onClick = { viewModel.sendText(prompt) },
                                enabled = !state.isSending,
                                label = { Text(stringResource(quickAction.first)) },
                            )
                        }
                    }
                    // 能力 chip 回显区：开启的模式可见可撤（EchoFlow「菜单开启 + chip 回显」范式）
                    val mode by viewModel.chatMode.collectAsState()
                    if (mode != whl.trending.chat.model.ChatMode.Normal) {
                        val isResearch = mode == whl.trending.chat.model.ChatMode.DeepResearch
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)) {
                            androidx.compose.material3.InputChip(
                                selected = true,
                                onClick = {
                                    if (isResearch) viewModel.toggleDeepResearch() else viewModel.toggleWebSearch()
                                },
                                label = {
                                    Text(stringResource(if (isResearch) R.string.chat_deep_research else R.string.chat_web_search))
                                },
                                trailingIcon = {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = stringResource(R.string.chat_dialog_cancel),
                                        modifier = Modifier.padding(0.dp),
                                    )
                                },
                            )
                        }
                    }
                    // 常驻模型选择器（≤1 个模型时自动隐藏）
                    val models by viewModel.models.collectAsState()
                    ModelPicker(
                        models = models,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    ChatInputBar(
                        input = state.input,
                        canSend = state.canSend,
                        pendingImages = state.pendingImages,
                        searchActive = mode == whl.trending.chat.model.ChatMode.WebSearch,
                        researchActive = mode == whl.trending.chat.model.ChatMode.DeepResearch,
                        onToggleSearch = viewModel::toggleWebSearch,
                        onToggleResearch = viewModel::toggleDeepResearch,
                        onInputChange = viewModel::updateInput,
                        onSend = viewModel::send,
                        onAddImage = viewModel::addPendingImage,
                        onRemoveImage = viewModel::removePendingImage,
                    )
                }
            },
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (state.messages.isEmpty()) {
                    // 尚无对话：展示欢迎区（实验性 + 每日额度说明）。发出第一条后 messages 非空，
                    // 自动切到 MessageList，与「介绍这个项目」chip 的隐藏时机一致。
                    ChatWelcome(hasContext = initialContext != null)
                } else {
                    MessageList(
                        messages = state.messages,
                        isSending = state.isSending,
                        onRetry = viewModel::retry,
                    )
                }
            }
        }
    }
}
