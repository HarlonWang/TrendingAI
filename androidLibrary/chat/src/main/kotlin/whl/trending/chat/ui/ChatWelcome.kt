package whl.trending.chat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import whl.trending.chat.R

/**
 * 空状态欢迎区，尚无任何对话时显示。上下文解读入口（[hasContext] = true）的标题与快捷问
 * 已在别处呈现，只保留额度说明一行。
 */
@Composable
fun ChatWelcome(hasContext: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (!hasContext) {
            Text(text = "✨", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.chat_assistant_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(16.dp))
        }
        Text(
            text = stringResource(R.string.chat_quota_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        // 模型出处不做视觉强调：OpenAI 品牌指南要求其展示不得比我们自己的名称更显著
        Text(
            text = stringResource(R.string.chat_model_provider),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatWelcomeGeneralPreview() {
    MaterialTheme { ChatWelcome(hasContext = false) }
}

@Preview(showBackground = true)
@Composable
private fun ChatWelcomeContextPreview() {
    MaterialTheme { ChatWelcome(hasContext = true) }
}
