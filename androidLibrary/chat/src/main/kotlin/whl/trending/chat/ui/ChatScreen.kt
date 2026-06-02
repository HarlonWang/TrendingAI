package whl.trending.chat.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import whl.trending.ai.chat.ChatContext
import whl.trending.chat.ChatViewModel
import whl.trending.chat.R
import whl.trending.chat.engine.ChatApi
import whl.trending.chat.engine.ChatEngine

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
    engine: ChatEngine = remember { ChatApi() },
    initialMessages: List<whl.trending.chat.model.ChatMessage> = emptyList(),
) {
    val viewModel: ChatViewModel = viewModel { ChatViewModel(engine, initialContext, initialMessages) }
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(initialContext?.title ?: stringResource(R.string.chat_assistant_title))
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
            ChatInputBar(
                input = state.input,
                canSend = state.canSend,
                onInputChange = viewModel::updateInput,
                onSend = viewModel::send,
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            MessageList(
                messages = state.messages,
                isSending = state.isSending,
                onRetry = viewModel::retry,
            )
        }
    }
}
