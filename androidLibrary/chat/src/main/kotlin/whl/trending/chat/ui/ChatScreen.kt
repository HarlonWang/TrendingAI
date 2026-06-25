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
import whl.trending.ai.core.platform.trackEvent
import whl.trending.chat.ChatViewModel
import whl.trending.chat.R
import whl.trending.chat.engine.ChatEngine
import whl.trending.chat.engine.byok.ByokChatEngine
import whl.trending.chat.engine.byok.ByokConfigStore
import whl.trending.chat.engine.byok.analyticsName
import whl.trending.chat.engine.byok.resolveChatEngine

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
 * 上报本次聊天实际命中的引擎（BYOK 采纳率核心指标）。
 * 仅上报低基数枚举：`engine`（byok/backend）+ byok 时的 `provider`，绝不含 key/baseUrl/model。
 */
private fun reportEngineResolved(engine: ChatEngine) {
    if (engine is ByokChatEngine) {
        trackEvent(
            "byok_chat_engine_resolved",
            mapOf("engine" to "byok", "provider" to ByokConfigStore.current().provider.analyticsName()),
        )
    } else {
        trackEvent("byok_chat_engine_resolved", mapOf("engine" to "backend"))
    }
}

/**
 * 全屏聊天页。通用入口传 [initialContext] = null；带上下文入口传具体条目。
 *
 * @param engine 显式注入的引擎（Demo 注入 FakeChatEngine）；为 null 时进入聊天按当前 BYOK 配置
 *   解析（启用且完整→直连，否则后端共享），见 [resolveChatEngine]。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    initialContext: ChatContext?,
    onBack: () -> Unit,
    engine: ChatEngine? = null,
    initialMessages: List<whl.trending.chat.model.ChatMessage> = emptyList(),
) {
    val sessionKey = sessionKeyOf(initialContext)
    val viewModel: ChatViewModel =
        viewModel(key = sessionKey) {
            // 仅在真实解析（非 Demo 注入）时上报实际命中的引擎，统计 BYOK 采纳率
            val resolved = engine ?: resolveChatEngine().also(::reportEngineResolved)
            ChatViewModel(resolved, initialContext, initialMessages)
        }
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
                    onRetry = viewModel::retry,
                )
            }
        }
    }
}
