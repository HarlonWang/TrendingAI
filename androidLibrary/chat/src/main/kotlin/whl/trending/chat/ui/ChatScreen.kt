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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import whl.trending.ai.chat.ChatContext
import whl.trending.chat.ChatViewModel
import whl.trending.chat.R
import whl.trending.chat.engine.ChatApi
import whl.trending.chat.engine.ChatEngine

/**
 * 按入口计算稳定的会话 key，使 Activity 级 ViewModelStore 按会话线各自缓存一个
 * [ChatViewModel]，从而实现"按会话线隔离 + 再次进入恢复"。
 *
 * - 首页通用助手：[context] == null → 固定唯一的 `"chat:general"`。
 * - 仓库详情：优先用 [ChatContext.sourceUrl]（仓库 URL，天然唯一稳定），
 *   为空时回退 [ChatContext.title]（即 `owner/repo`，同样唯一）。
 */
private fun sessionKeyOf(context: ChatContext?): String =
    if (context == null) "chat:general"
    else "chat:" + (context.sourceUrl ?: context.title)

/**
 * 全屏聊天页。通用入口传 [initialContext] = null；带上下文入口传具体条目。
 *
 * @param engine 默认正式引擎 [ChatApi]；Demo 可注入 FakeChatEngine。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    initialContext: ChatContext?,
    onBack: () -> Unit,
    engine: ChatEngine = ChatApi.shared,
    initialMessages: List<whl.trending.chat.model.ChatMessage> = emptyList(),
) {
    val sessionKey = sessionKeyOf(initialContext)
    val viewModel: ChatViewModel =
        viewModel(key = sessionKey) { ChatViewModel(engine, initialContext, initialMessages) }
    val state by viewModel.uiState.collectAsState()

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
            )
        },
        bottomBar = {
            Column {
                // 仅 README 入口、且尚无对话时显示"这个项目是做什么的"快捷问。
                // messages 非空即隐藏，天然覆盖"发送后隐藏"与"恢复历史会话不再显示"。
                if (initialContext?.sourceUrl != null && state.messages.isEmpty()) {
                    val prompt = stringResource(R.string.chat_action_what_is_this_prompt)
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                        AssistChip(
                            onClick = { viewModel.sendText(prompt) },
                            enabled = !state.isSending,
                            label = { Text(stringResource(R.string.chat_action_what_is_this)) },
                        )
                    }
                }
                ChatInputBar(
                    input = state.input,
                    canSend = state.canSend,
                    onInputChange = viewModel::updateInput,
                    onSend = viewModel::send,
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
