package whl.trending.chat.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import whl.trending.ai.chat.ChatContext
import whl.trending.ai.ui.common.TrendingScaffold
import whl.trending.ai.ui.common.TrendingTopAppBar
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

    // README 详情页「深度调研」入口：只预选模式 + 预填主题，发送仍由用户确认——
    // research 计价高（10 credits），不做自动触发（有别于解读入口的 auto 语义）
    val researchPrefill = stringResource(R.string.chat_research_repo_prefill)
    LaunchedEffect(entryKey) {
        if (initialContext?.autoDeepResearch == true) {
            viewModel.enableDeepResearch()
            if (viewModel.uiState.value.input.isBlank()) viewModel.updateInput(researchPrefill)
        }
    }

    // AI 一开始回复就收起键盘：把屏幕让给正文，用户不用先手动关键盘才能读回复。
    // clearFocus 同时清焦点（而非只 hide），避免留下「键盘没了但光标还在闪」的中间态。
    val focusManager = LocalFocusManager.current
    LaunchedEffect(state.isSending) {
        if (state.isSending) focusManager.clearFocus()
    }

    // 进页面自动聚焦输入框唤起键盘（焦点请求在 ChatInputBar 内部）：主路径是「进来就想打字」。
    // 「一键解读」入口例外：它进来就自动发送，上面的 isSending 分支会立刻 clearFocus，
    // 两者叠加就是键盘弹起又秒收的闪烁，索性不弹。
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
        TrendingScaffold(
            topBar = {
                TrendingTopAppBar(
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
                val mode by viewModel.chatMode.collectAsState()
                val catalog by viewModel.catalog.collectAsState()
                Column {
                    // ① 建议动作行：点一下立刻发出一条消息，一次性、发完即走。全部用描边样式，
                    //    与下面填充的「当前配置」行分层——描边 = 建议，填充 = 已生效的状态。
                    //    两个按钮可能同时出现（GitHub 条目 + 尚无对话），排一行而不是各占一行。
                    //    用 OutlinedButton 而不是 AssistChip：Expressive 的按钮默认 40dp 高 + 全圆，
                    //    与输入胶囊同一套圆角语言；chip 的 8dp 小圆角搁在 36dp 的胶囊上方不搭。
                    //
                    // 「一键详细解读」：GitHub 条目 + README 达标 + 尚无成功解读 → 常驻显示
                    // （不同于介绍 chip 的「messages 为空才显示」）；生成中置灰，成功一次后隐藏。
                    val detailVisible = DetailSummaryPolicy.chipVisible(initialContext, state.messages)
                    // 尚无对话时显示入口对应的快捷问（label 资源 to prompt 资源）：通用助手入口问能力，
                    // README 入口问项目介绍。messages 非空即隐藏，天然覆盖「发送后隐藏」与
                    // 「恢复历史会话不再显示」。
                    val quickAction = when {
                        initialContext == null ->
                            R.string.chat_action_what_can_you_do to R.string.chat_action_what_can_you_do_prompt
                        initialContext.sourceUrl != null ->
                            R.string.chat_action_what_is_this to R.string.chat_action_what_is_this_prompt
                        else -> null
                    }
                    val quickVisible = quickAction != null && state.messages.isEmpty()
                    if (detailVisible || quickVisible) {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                // 下方留 8dp：与「当前配置」行拉开一档，两组 chip 才读得出是两类东西
                                .padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (detailVisible) {
                                val detailPrompt = stringResource(R.string.chat_action_detail_summary)
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
                    // ② 当前配置行：模型 + 已开启的能力，回答「下一条消息以什么配置发出去」
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
                    // 尚无对话：展示欢迎区（实验性 + 每日额度说明）。发出第一条后 messages 非空，
                    // 自动切到 MessageList，与「介绍这个项目」chip 的隐藏时机一致。
                    ChatWelcome(hasContext = initialContext != null)
                } else {
                    // topic 用预填调研主题而非按钮 CTA 文案：与 FAB 入口同构，后端拿到的是
                    // 有内容的调研诉求（Sourcery 审查采纳）
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
