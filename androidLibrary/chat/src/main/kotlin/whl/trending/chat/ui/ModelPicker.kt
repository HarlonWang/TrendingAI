package whl.trending.chat.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import whl.trending.ai.core.Constants
import whl.trending.ai.core.platform.trackEvent
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.data.model.ChatModelOption
import whl.trending.chat.R

/**
 * 常驻模型选择器（对所有用户透出）。默认显示免费 gpt-5.4；Pro 专属项对免费用户锁定，
 * 点锁定项 → 埋点 + 跳 Sponsors（第二个、更早的转化入口）。
 *
 * 只有一个可选模型时不渲染（无从选择、也无 Pro 项可 upsell）。
 */
@Composable
internal fun ModelPicker(
    models: List<ChatModelOption>,
    modifier: Modifier = Modifier,
) {
    if (models.size <= 1) return

    val context = LocalContext.current
    val isPro by globalSettingsManager.isPro.collectAsState(initial = globalSettingsManager.getIsProSync())
    val selectedId by globalSettingsManager.selectedChatModel
        .collectAsState(initial = globalSettingsManager.getSelectedChatModelSync())
    var expanded by remember { mutableStateOf(false) }

    val current = models.firstOrNull { it.id == selectedId } ?: models.first()
    val hasLocked = models.any { it.proOnly && !isPro }

    Box(modifier) {
        AssistChip(
            onClick = {
                expanded = true
                // 打开选择器且存在锁定项 = 模型入口 upsell 曝光
                if (hasLocked) {
                    trackEvent("pro_upsell_shown", mapOf("trigger" to TRIGGER_MODEL_LOCKED))
                }
            },
            label = { Text(current.name) },
            trailingIcon = {
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
            },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            models.forEach { model ->
                val locked = model.proOnly && !isPro
                DropdownMenuItem(
                    text = { Text(model.name) },
                    trailingIcon = if (locked) {
                        {
                            Icon(
                                Icons.Filled.Lock,
                                contentDescription = "Pro",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else null,
                    onClick = {
                        expanded = false
                        if (locked) {
                            trackEvent("pro_upsell_clicked", mapOf("trigger" to TRIGGER_MODEL_LOCKED))
                            Toast.makeText(
                                context,
                                context.getString(R.string.chat_model_pro_locked, model.name),
                                Toast.LENGTH_SHORT,
                            ).show()
                            openUrl(context, Constants.GITHUB_SPONSORS_URL)
                        } else {
                            globalSettingsManager.setSelectedChatModel(model.id)
                        }
                    },
                )
            }
        }
    }
}
