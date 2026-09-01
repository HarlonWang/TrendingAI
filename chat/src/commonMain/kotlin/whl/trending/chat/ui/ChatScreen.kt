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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import whl.trending.chat.ChatViewModel
import trendingai.chat.generated.resources.Res
import trendingai.chat.generated.resources.chat_assistant_title
import trendingai.chat.generated.resources.chat_back
import trendingai.chat.generated.resources.chat_history
import whl.trending.chat.engine.ChatApi
import whl.trending.chat.engine.ChatEngine
import whl.trending.chat.store.InMemoryChatStore
import whl.trending.chat.store.rememberDefaultChatStore

/** 空会话欢迎态的建议动作：点击即以 [prompt] 发送一条普通消息（宿主注入） */
data class ChatSuggestion(val label: String, val prompt: String)

/**
 * 全屏聊天页。单 ViewModel（key 固定）+ 会话抽屉：VM 挂 Activity 作用域，
 * 进程内再次进入续接现场，新会话经抽屉「新会话」产生。
 *
 * @param engine 默认正式引擎 [ChatApi]；Demo 可注入 FakeChatEngine。
 * @param persistent Demo/预览可关（纯内存模式）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    engine: ChatEngine = ChatApi.shared,
    initialMessages: List<whl.trending.chat.model.ChatMessage> = emptyList(),
    persistent: Boolean = true,
    suggestions: List<ChatSuggestion> = emptyList(),
) {
    val store = if (persistent) rememberDefaultChatStore() else remember { InMemoryChatStore() }
    val viewModel: ChatViewModel = viewModel(key = "chat") {
        ChatViewModel(engine, initialMessages, store)
    }
    val state by viewModel.uiState.collectAsState()
    val threads by viewModel.threads.collectAsState()
    val currentThreadId by viewModel.currentThreadId.collectAsState()

    // AI 一开始回复就收起键盘；clearFocus 而非只 hide，避免「键盘没了但光标还在闪」
    val focusManager = LocalFocusManager.current
    LaunchedEffect(state.isSending) {
        if (state.isSending) focusManager.clearFocus()
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
        ChatScaffold(
            topBar = {
                ChatTopAppBar(
                    title = {
                        Text(
                            text = stringResource(Res.string.chat_assistant_title),
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
                val searchActive by viewModel.searchEnabled.collectAsState()
                val catalog by viewModel.catalog.collectAsState()
                Column {
                    // 建议动作行（描边 = 建议、填充的「当前配置」行 = 已生效状态，靠样式分层）：
                    // 宿主按入口注入，仅空会话欢迎态展示——发送后与恢复历史会话都自然隐藏
                    if (suggestions.isNotEmpty() && state.messages.isEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            suggestions.forEach { suggestion ->
                                OutlinedButton(
                                    onClick = { viewModel.sendText(suggestion.prompt) },
                                    enabled = !state.isSending,
                                ) {
                                    Text(suggestion.label)
                                }
                            }
                        }
                    }
                    // 当前配置行：回答「下一条消息以什么配置发出去」
                    ChatContextRow(
                        catalog = catalog,
                        searchActive = searchActive,
                        onToggleSearch = viewModel::toggleWebSearch,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    ChatInputBar(
                        input = state.input,
                        canSend = state.canSend,
                        pendingImages = state.pendingImages,
                        searchActive = searchActive,
                        onToggleSearch = viewModel::toggleWebSearch,
                        onInputChange = viewModel::updateInput,
                        onSend = viewModel::send,
                        onAddImage = viewModel::addPendingImage,
                        onRemoveImage = viewModel::removePendingImage,
                        autoFocus = true,
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
                    ChatWelcome()
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
