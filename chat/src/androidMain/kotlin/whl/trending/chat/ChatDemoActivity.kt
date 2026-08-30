package whl.trending.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import whl.trending.chat.sample.FakeChatEngine
import whl.trending.chat.sample.SampleData
import whl.trending.chat.ui.ChatScreen

/**
 * 离线 Demo 入口：内置示例会话 + 假引擎，无需接 API 即可验收展示与交互效果。
 *
 * 启动方式：
 *  - debug 包桌面图标「Chat Demo」（见 androidApp/src/debug 的 activity-alias）
 *  - 或 adb：`adb shell am start -n whl.trending.ai.debug/whl.trending.chat.ChatDemoActivity`
 */
class ChatDemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // adb 传 `--ez real true` 用真实 ChatApi 连生产联调；否则示例 Demo（假引擎）
        val real = intent.getBooleanExtra("real", false)
        setContent { DemoTheme { DemoContent(real = real, onBack = ::finish) } }
    }
}

@Composable
private fun DemoTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}

@Composable
private fun DemoContent(real: Boolean, onBack: () -> Unit) {
    if (real) {
        // 真实引擎（ChatScreen 默认即 ChatApi），空会话连生产
        ChatScreen(initialContext = null, onBack = onBack)
    } else {
        ChatScreen(
            initialContext = null,
            onBack = onBack,
            engine = androidx.compose.runtime.remember { FakeChatEngine() },
            initialMessages = SampleData.messages,
            // 纯内存：假引擎的样例会话不落真库
            persistent = false,
        )
    }
}
