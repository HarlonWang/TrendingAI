package whl.trending.chat.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import whl.trending.chat.ChatContext
import whl.trending.chat.ChatViewModel
import whl.trending.chat.DetailSummaryPolicy
import trendingai.chat.generated.resources.Res
import trendingai.chat.generated.resources.chat_action_detail_summary
import trendingai.chat.generated.resources.chat_action_what_can_you_do
import trendingai.chat.generated.resources.chat_action_what_can_you_do_prompt
import trendingai.chat.generated.resources.chat_action_what_is_this
import trendingai.chat.generated.resources.chat_action_what_is_this_prompt
import trendingai.chat.generated.resources.chat_assistant_title
import trendingai.chat.generated.resources.chat_back
import trendingai.chat.generated.resources.chat_history
import trendingai.chat.generated.resources.chat_research_repo_prefill
import whl.trending.chat.engine.ChatApi
import whl.trending.chat.engine.ChatEngine
import whl.trending.chat.store.ChatStore
import whl.trending.chat.store.InMemoryChatStore
import whl.trending.chat.store.rememberDefaultChatStore

/** 入口键，驱动「同一入口只 enterEntry 一次」 */
private fun entryKeyOf(context: ChatContext?): String = ChatStore.entryKeyOf(context)

/**
 * 全屏聊天页。通用入口传 [initialContext] = null；带上下文入口传具体条目。
 * 单 ViewModel（key 固定）+ 会话抽屉：进入时 [ChatViewModel.enterEntry] 恢复该入口最近会话（跨进程）。
 *
 * @param engine 默认正式引擎 [ChatApi]；Demo 可注入 FakeChatEngine。
 * @param persistent Demo/预览可关（纯内存模式）
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
    val store = if (persistent) rememberDefaultChatStore() else remember { InMemoryChatStore() }
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

    // 「一键解读」入口：进入会话自动触发一次详细解读；chipVisible 判定天然幂等、防重复触发
    val autoDetailPrompt = stringResource(Res.string.chat_action_detail_summary)
    LaunchedEffect(entryKey) {
        if (initialContext?.autoDetailSummary == true &&
            DetailSummaryPolicy.chipVisible(initialContext, viewModel.uiState.value.messages)
        ) {
            viewModel.sendDetailSummary(autoDetailPrompt)
        }
    }

    // 解读卡尾部「深度调研此项目」升级漏斗的调研主题（见下方 onResearchUpsell）
    val researchPrefill = stringResource(Res.string.chat_research_repo_prefill)

    // AI 一开始回复就收起键盘；clearFocus 而非只 hide，避免「键盘没了但光标还在闪」
    val focusManager = LocalFocusManager.current
    LaunchedEffect(state.isSending) {
        if (state.isSending) focusManager.clearFocus()
    }

    // 进页面自动聚焦唤起键盘；「一键解读」入口例外——它进来即发送、isSending 分支会立刻
    // clearFocus，叠加就是键盘弹起又秒收的闪烁
    val autoFocusInput = initialContext?.autoDetailSummary != true

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
        ChatScaffold(
            topBar = {
                ChatTopAppBar(
                    title = {
                        Text(
                            text = initialContext?.title
                                ?: stringResource(Res.string.chat_assistant_title),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(Res.string.chat_back),
                            )
                        }
                    },
                    actions = {
                        // 会话抽屉入口（纯内存模式无历史可言，隐藏）
                        if (persistent) {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(
                                    imageVector = Icons.Filled.Menu,
                                    contentDescription = stringResource(Res.string.chat_history),
                                )
                            }
                        }
                    },
                )
            },
            bottomBar = {
                val mode by viewModel.chatMode.collectAsState()
                val catalog by viewModel.catalog.collectAsState()
                Column {
                    // 建议动作行：描边 = 建议、填充的「当前配置」行 = 已生效状态，靠样式分层
                    val detailVisible = DetailSummaryPolicy.chipVisible(initialContext, state.messages)
                    // 快捷问按入口选（label 资源 to prompt 资源）；messages 非空即隐藏，
                    // 天然覆盖「发送后隐藏」与「恢复历史会话不再显示」
                    val quickAction = when {
                        initialContext == null ->
                            Res.string.chat_action_what_can_you_do to Res.string.chat_action_what_can_you_do_prompt
                        initialContext.sourceUrl != null ->
                            Res.string.chat_action_what_is_this to Res.string.chat_action_what_is_this_prompt
                        else -> null
                    }
                    val quickVisible = quickAction != null && state.messages.isEmpty()
                    if (detailVisible || quickVisible) {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (detailVisible) {
                                val detailPrompt = stringResource(Res.string.chat_action_detail_summary)
                                OutlinedButton(
                                    onClick = { viewModel.sendDetailSummary(detailPrompt) },
                                    enabled = !state.isSending,
                                ) {
                                    Text(detailPrompt)
                                }
                            }
                            if (quickAction != null && quickVisible) {
                                val prompt = stringResource(quickAction.second)
                                OutlinedButton(
                                    onClick = { viewModel.sendText(prompt) },
                                    enabled = !state.isSending,
                                ) {
                                    Text(stringResource(quickAction.first))
                                }
                            }
                        }
                    }
                    // 当前配置行：回答「下一条消息以什么配置发出去」
                    ChatContextRow(
                        catalog = catalog,
                        mode = mode,
                        onToggleSearch = viewModel::toggleWebSearch,
                        onToggleResearch = viewModel::toggleDeepResearch,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    ChatInputBar(
                        input = state.input,
                        // research 仅支持文本：只有图片没文字时发送会被 VM 忽略，按钮同步禁用（不静默）
                        canSend = state.canSend &&
                            (mode != whl.trending.chat.model.ChatMode.DeepResearch || state.input.isNotBlank()),
                        pendingImages = state.pendingImages,
                        searchActive = mode == whl.trending.chat.model.ChatMode.WebSearch,
                        researchActive = mode == whl.trending.chat.model.ChatMode.DeepResearch,
                        onToggleSearch = viewModel::toggleWebSearch,
                        onToggleResearch = viewModel::toggleDeepResearch,
                        onInputChange = viewModel::updateInput,
                        onSend = viewModel::send,
                        onAddImage = viewModel::addPendingImage,
                        onRemoveImage = viewModel::removePendingImage,
                        autoFocus = autoFocusInput,
                    )
                }
            },
        ) { padding ->
            Box(
                modifier = Modifier.fillMaxSize()
                    .padding(padding)
                    // 点消息区/欢迎区收起键盘。detectTapGestures 走 Main pass，
                    // 消息里的链接点击、列表滚动等子手势照常先消费，不会被这里抢走
                    .pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } },
            ) {
                if (state.messages.isEmpty()) {
                    ChatWelcome(hasContext = initialContext != null)
                } else {
                    // topic 用预填调研主题而非按钮 CTA 文案：后端拿到的才是有内容的调研诉求
                    MessageList(
                        messages = state.messages,
                        isSending = state.isSending,
                        onRetry = viewModel::retry,
                        onResearchUpsell = { viewModel.sendRepoResearch(researchPrefill) },
                    )
                }
            }
        }
    }
}
