package whl.trending.chat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import whl.trending.chat.ByokSettingsViewModel
import whl.trending.chat.FetchState
import whl.trending.chat.R
import whl.trending.chat.engine.byok.ByokProvider
import whl.trending.chat.model.ChatErrorCategory

/** BYOK「自定义 AI 模型」设置页。字段改动即时持久化，退出即生效。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ByokSettingsScreen(
    onBack: () -> Unit,
    viewModel: ByokSettingsViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.byok_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.chat_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 启用开关
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.byok_enable), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.byok_enable_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.enabled, onCheckedChange = viewModel::setEnabled)
            }

            // 关闭时下方表单整体灰掉但保留已填值（仍可见，便于再次开启）
            val formEnabled = state.enabled

            // Provider 选择
            Text(stringResource(R.string.byok_provider), style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val providers = ByokProvider.entries
                providers.forEachIndexed { index, p ->
                    SegmentedButton(
                        selected = state.provider == p,
                        onClick = { viewModel.setProvider(p) },
                        enabled = formEnabled,
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = providers.size),
                        label = { Text(providerLabel(p)) },
                    )
                }
            }

            // Base URL
            OutlinedTextField(
                value = state.baseUrl,
                onValueChange = viewModel::setBaseUrl,
                label = { Text(stringResource(R.string.byok_base_url)) },
                singleLine = true,
                enabled = formEnabled,
                modifier = Modifier.fillMaxWidth(),
            )

            // API Key（密码态 + 眼睛切换）
            var keyVisible by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = state.apiKey,
                onValueChange = viewModel::setApiKey,
                label = { Text(stringResource(R.string.byok_api_key)) },
                singleLine = true,
                enabled = formEnabled,
                visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { keyVisible = !keyVisible }, enabled = formEnabled) {
                        Icon(
                            imageVector = if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = stringResource(
                                if (keyVisible) R.string.byok_api_key_hide else R.string.byok_api_key_show,
                            ),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            // 模型区
            Text(stringResource(R.string.byok_model), style = MaterialTheme.typography.labelLarge)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(onClick = viewModel::fetchModels, enabled = state.canFetch) {
                    if (state.fetch == FetchState.Loading) {
                        LoadingIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(stringResource(R.string.byok_fetch_models))
                    }
                }
                FetchStatus(state.fetch)
            }

            // 启用但 Base URL / Key 未填齐时，提示需先填写（拉取按钮此时不可用）
            if (formEnabled && (state.baseUrl.isBlank() || state.apiKey.isBlank())) {
                Text(
                    text = stringResource(R.string.byok_fill_first),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 模型选择：下拉（拉取成功且非手动）或手动输入
            if (state.manualModel || state.models.isEmpty()) {
                OutlinedTextField(
                    value = state.model,
                    onValueChange = viewModel::setModel,
                    label = { Text(stringResource(R.string.byok_manual_model)) },
                    placeholder = { Text(stringResource(R.string.byok_manual_model_hint)) },
                    singleLine = true,
                    enabled = formEnabled,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                ModelDropdown(
                    models = state.models,
                    selected = state.model,
                    enabled = formEnabled,
                    onSelect = viewModel::setModel,
                    onManual = { viewModel.toggleManualModel(true) },
                )
            }

            Spacer(Modifier.height(8.dp))
            PrivacyNote()
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** 安全与隐私说明：key 本机加密、不上传、请求直连厂商。 */
@Composable
private fun PrivacyNote() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(14.dp)) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(R.string.byok_privacy_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

@Composable
private fun FetchStatus(state: FetchState) {
    when (state) {
        is FetchState.Success -> Text(
            text = stringResource(R.string.byok_fetch_success, state.count),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodySmall,
        )
        is FetchState.Failed -> Text(
            text = stringResource(fetchErrorRes(state.error.category)),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
        else -> Unit
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelDropdown(
    models: List<String>,
    selected: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    onManual: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = it },
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(stringResource(R.string.byok_model)) },
            placeholder = { Text(stringResource(R.string.byok_model_placeholder)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            models.forEach { m ->
                DropdownMenuItem(
                    text = { Text(m) },
                    onClick = {
                        onSelect(m)
                        expanded = false
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.byok_manual_model)) },
                onClick = {
                    onManual()
                    expanded = false
                },
            )
        }
    }
}

@Composable
private fun providerLabel(provider: ByokProvider): String = when (provider) {
    ByokProvider.OPENAI_COMPATIBLE -> stringResource(R.string.byok_provider_openai)
    ByokProvider.ANTHROPIC -> stringResource(R.string.byok_provider_anthropic)
}

private fun fetchErrorRes(category: ChatErrorCategory): Int = when (category) {
    ChatErrorCategory.AUTH -> R.string.chat_error_auth
    ChatErrorCategory.NETWORK -> R.string.chat_error_network
    ChatErrorCategory.TIMEOUT -> R.string.chat_error_timeout
    ChatErrorCategory.SERVER -> R.string.chat_error_server
    ChatErrorCategory.BAD_REQUEST -> R.string.chat_error_bad_request
    ChatErrorCategory.QUOTA -> R.string.chat_quota_exceeded
    ChatErrorCategory.UNKNOWN -> R.string.chat_error_message
}
