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
import androidx.compose.runtime.LaunchedEffect
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
import whl.trending.ai.data.model.DEFAULT_CHAT_MODEL
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

    // 自愈守卫：若持久化的选择对本用户已锁定（Pro 过期未登出、或上个 Pro 用户遗留），
    // 复位到默认免费模型。一处同时修好「chip 显示」与「ChatApi 透传」的一致性。
    LaunchedEffect(models, isPro) {
        val sel = models.firstOrNull { it.id == selectedId }
        if (models.isNotEmpty() && (sel == null || (sel.proOnly && !isPro))) {
            globalSettingsManager.setSelectedChatModel(DEFAULT_CHAT_MODEL)
        }
    }

    // 展示同样带 tier 守卫：锁定项永不显示为当前选择（覆盖自愈生效前的那一帧）
    val current = models.firstOrNull { it.id == selectedId }?.takeIf { !(it.proOnly && !isPro) }
        ?: models.firstOrNull { !it.proOnly }
        ?: models.first()
    val hasLocked = models.any { it.proOnly && !isPro }

    Box(modifier) {
        AssistChip(
            onClick = {
                expanded = true
                // 模型入口 upsell 曝光：每次展开下拉、且存在锁定项，各算一次曝光（类广告 impression）。
                // 语义与 chat_quota（LaunchedEffect(Unit) 每次挂载去重一次）不同——model_locked 是「每次看」，
                // 对比 shown→clicked 漏斗时需按 source 分开看，勿直接横比。
                if (hasLocked) {
                    trackEvent("pro_upsell_shown", mapOf(UPSELL_SOURCE_KEY to SOURCE_MODEL_LOCKED))
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
                            trackEvent("pro_upsell_clicked", mapOf(UPSELL_SOURCE_KEY to SOURCE_MODEL_LOCKED))
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
