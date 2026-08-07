package whl.trending.chat.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.data.model.FOLLOW_SERVER_DEFAULT
import whl.trending.ai.data.model.ChatModelOption
import whl.trending.ai.data.model.ChatModelsResponse
import whl.trending.ai.data.model.catalogDefaultChatModel
import whl.trending.ai.data.model.resolveDisplayedChatModel
import whl.trending.ai.data.model.resolveEffectiveChatModel
import whl.trending.chat.R

/**
 * 常驻模型选择器（对所有用户透出）。未手选时显示目录里的免费默认项；Pro 专属项对免费用户锁定，
 * 点锁定项 → 纯告知弹窗（说明该模型属于 Pro，可继续用默认模型），不外跳赞助页。
 *
 * 2026-07-26 起此处不再承担 Pro 转化：与配额触顶卡同一决策——功能受限的当下推销观感差，
 * Pro 信息统一留在账户页由用户主动了解。
 *
 * 只有一个可选模型时不渲染（无从选择，锁定项也无从触达）。
 */
@Composable
internal fun ModelPicker(
    catalog: ChatModelsResponse,
    modifier: Modifier = Modifier,
) {
    val models = catalog.models
    if (models.size <= 1) return
    // 契约破损守卫：default 不可解析（缺失/悬空，正常不该发生）时整体缺席、不接受任何写入——
    // 否则「点默认项记未手选」分支会静默失效，把用户的默认意图误写成钉住具体 id
    val catalogDefault = catalogDefaultChatModel(catalog) ?: return

    val isPro by globalSettingsManager.isPro.collectAsState(initial = globalSettingsManager.currentIsPro())
    val selectedId by globalSettingsManager.chatModelChoice
        .collectAsState(initial = globalSettingsManager.currentChatModelChoice())
    var expanded by remember { mutableStateOf(false) }
    // 点锁定项弹纯告知弹窗：说明这是 Pro 模型、默认模型仍可用，单按钮关闭，不外跳
    var unlockDialogModel by remember { mutableStateOf<ChatModelOption?>(null) }

    unlockDialogModel?.let { model ->
        AlertDialog(
            onDismissRequest = { unlockDialogModel = null },
            title = { Text(stringResource(R.string.chat_model_unlock_title)) },
            text = { Text(stringResource(R.string.chat_model_unlock_message, model.name)) },
            confirmButton = {
                TextButton(onClick = { unlockDialogModel = null }) {
                    Text(stringResource(R.string.chat_model_unlock_dismiss))
                }
            },
        )
    }

    // 自愈守卫：若钉住的模型对本用户已锁定（Pro 过期未登出、或上个 Pro 用户遗留）或已下架，
    // 回到跟随服务端默认——不改钉成另一个具体 id，默认是谁由服务端说了算。判定与 ChatApi 发送
    // 共用 resolveEffectiveChatModel——即使本组件未挂载，发送侧也会按同一规则兜底。
    LaunchedEffect(models, isPro) {
        if (selectedId != FOLLOW_SERVER_DEFAULT && resolveEffectiveChatModel(models, selectedId, isPro) == null) {
            globalSettingsManager.followServerDefault()
        }
    }

    // 展示同样带 tier 守卫：锁定项永不显示为当前选择（覆盖自愈生效前的那一帧）。
    // 上方守卫已保证 default 可解析，这里必非空；?: 仅作类型收窄
    val current = resolveDisplayedChatModel(catalog, selectedId, isPro) ?: catalogDefault

    Box(modifier) {
        AssistChip(
            onClick = { expanded = true },
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
                        when {
                            locked -> unlockDialogModel = model
                            // 选「默认项」记为跟随服务端默认而非钉住这个 id：否则后端换默认模型时，
                            // 只是点过一次默认的用户会被永久留在旧模型上——正是要解掉的耦合
                            model.id == catalogDefault.id ->
                                globalSettingsManager.followServerDefault()
                            else -> globalSettingsManager.pinChatModel(model.id)
                        }
                    },
                )
            }
        }
    }
}
