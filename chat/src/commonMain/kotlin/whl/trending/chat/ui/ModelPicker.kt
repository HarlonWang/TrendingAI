package whl.trending.chat.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import whl.trending.chat.host.chatHost
import whl.trending.chat.model.FOLLOW_SERVER_DEFAULT
import whl.trending.chat.model.ChatModelOption
import whl.trending.chat.model.ChatModelsResponse
import whl.trending.chat.model.catalogDefaultChatModel
import whl.trending.chat.model.catalogProviderNames
import whl.trending.chat.model.resolveDisplayedChatModel
import whl.trending.chat.model.resolveEffectiveChatModel
import trendingai.chat.generated.resources.Res
import trendingai.chat.generated.resources.chat_list_separator
import trendingai.chat.generated.resources.chat_model_picker_title
import trendingai.chat.generated.resources.chat_model_provider
import trendingai.chat.generated.resources.chat_model_unlock_dismiss
import trendingai.chat.generated.resources.chat_model_unlock_message
import trendingai.chat.generated.resources.chat_model_unlock_title

/**
 * [ModelPicker] 是否有东西可渲染：顶栏标题槽据此决定放选择器还是回落文字标题。
 * 选择器是否出现只有这里知道——组件内部 return 掉的话，标题槽会空着。
 */
internal fun chatModelPickerVisible(catalog: ChatModelsResponse): Boolean =
    catalog.models.size > 1 && catalogDefaultChatModel(catalog) != null

/**
 * 常驻模型选择器（对所有用户透出）。未手选时显示目录里的免费默认项；Pro 专属项对免费用户锁定，
 * 点锁定项 → 纯告知弹窗（说明该模型属于 Pro，可继续用默认模型），不外跳赞助页。
 *
 * 2026-07-26 起此处不再承担 Pro 转化：与配额触顶卡同一决策——功能受限的当下推销观感差，
 * Pro 信息统一留在账户页由用户主动了解。
 *
 * 只有一个可选模型时不渲染（无从选择，锁定项也无从触达）。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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

    val isPro by chatHost.isPro.collectAsState(initial = chatHost.currentIsPro())
    val selectedId by chatHost.chatModelChoice
        .collectAsState(initial = chatHost.currentChatModelChoice())
    var expanded by remember { mutableStateOf(false) }
    // 点锁定项弹纯告知弹窗：说明这是 Pro 模型、默认模型仍可用，单按钮关闭，不外跳
    var unlockDialogModel by remember { mutableStateOf<ChatModelOption?>(null) }
    // 选「默认项」记为跟随服务端默认而非钉住这个 id：否则后端换默认模型时，
    // 只是点过一次默认的用户会被永久留在旧模型上——正是要解掉的耦合
    val select = { model: ChatModelOption ->
        if (model.id == catalogDefault.id) chatHost.followServerDefault() else chatHost.pinChatModel(model.id)
    }


    unlockDialogModel?.let { model ->
        AlertDialog(
            onDismissRequest = { unlockDialogModel = null },
            title = { Text(stringResource(Res.string.chat_model_unlock_title)) },
            text = { Text(stringResource(Res.string.chat_model_unlock_message, model.name)) },
            confirmButton = {
                TextButton(onClick = { unlockDialogModel = null }) {
                    Text(stringResource(Res.string.chat_model_unlock_dismiss))
                }
            },
        )
    }

    // 自愈守卫：若钉住的模型对本用户已锁定（Pro 过期未登出、或上个 Pro 用户遗留）或已下架，
    // 回到跟随服务端默认——不改钉成另一个具体 id，默认是谁由服务端说了算。判定与 ChatApi 发送
    // 共用 resolveEffectiveChatModel——即使本组件未挂载，发送侧也会按同一规则兜底。
    LaunchedEffect(models, isPro) {
        if (selectedId != FOLLOW_SERVER_DEFAULT && resolveEffectiveChatModel(models, selectedId, isPro) == null) {
            chatHost.followServerDefault()
        }
    }

    // 展示同样带 tier 守卫：锁定项永不显示为当前选择（覆盖自愈生效前的那一帧）。
    // 上方守卫已保证 default 可解析，这里必非空；?: 仅作类型收窄
    val current = resolveDisplayedChatModel(catalog, selectedId, isPro) ?: catalogDefault

    Box(modifier) {
        // M3 Expressive 的 SplitButton 而不是 chip：模型名与下拉箭头分成两块，「这是个选择器」
        // 由形态本身讲清楚，不用靠「能不能点掉」去猜（chip 的老问题）。
        // 挂在顶栏中央当标题，配色保持中性：模型是常驻信息，不该是页面第一眼的落点。
        // 顶栏底色是 surfaceContainer，取深两档的 Highest 才在浅色下看得出胶囊轮廓
        val modelColors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
        SplitButtonLayout(
            // 限宽：长模型名截断成省略号，别把顶栏两侧的图标挤出去
            modifier = Modifier.widthIn(max = 240.dp),
            leadingButton = {
                SplitButtonDefaults.LeadingButton(
                    onClick = { expanded = true },
                    colors = modelColors,
                ) {
                    Text(
                        text = current.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            },
            trailingButton = {
                SplitButtonDefaults.TrailingButton(
                    checked = expanded,
                    onCheckedChange = { expanded = it },
                    colors = modelColors,
                ) {
                    // 展开时箭头转 180°：M3 官方 SplitButton 示例的做法，组件本身不管这个
                    val rotation by animateFloatAsState(
                        targetValue = if (expanded) 180f else 0f,
                        label = "model-picker-arrow",
                    )
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier
                            .size(SplitButtonDefaults.TrailingIconSize)
                            .graphicsLayer { rotationZ = rotation },
                    )
                }
            },
        )
        // 底部浮层而不是下拉：6 项以上、带段头与锁图标，已超出下拉菜单的适用范围（仓库 UI 规范）；
        // 顶栏锚点的 Popup 定位在 edge-to-edge 下还会漂到状态栏上盖住选择器本身
        if (expanded) {
            ChatBottomSheet(
                onDismissRequest = { expanded = false },
                title = stringResource(Res.string.chat_model_picker_title),
            ) {
                // 多厂商时按厂商分节：节头只是小字标签，不可点；单厂商不加节头（信息与页脚重复）
                val grouped = models.groupBy { it.provider }
                val showGroupHeaders = grouped.size > 1
                grouped.values.forEach { group ->
                    if (showGroupHeaders) {
                        Text(
                            text = group.first().providerName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            // ListItem 自带 16dp 水平内边距，节头与页脚对齐到同一条线
                            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
                        )
                    }
                    group.forEach { model ->
                        val locked = model.proOnly && !isPro
                        ListItem(
                            headlineContent = { Text(model.name) },
                            trailingContent = {
                                when {
                                    locked -> Icon(
                                        Icons.Filled.Lock,
                                        contentDescription = "Pro",
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    model.id == current.id -> Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.medium)
                                .clickable {
                                    expanded = false
                                    when {
                                        locked -> unlockDialogModel = model
                                        else -> select(model)
                                    }
                                },
                        )
                    }
                }
                // 模型出处。放在浮层末尾而不是常驻行内：起疑的人会点开选择器（型号名就是疑问的原点）。
                // 视觉压到 bodySmall + onSurfaceVariant——OpenAI 品牌指南要求其展示不得比我们自己的
                // 名称更显著，且醒目的供应商声明本身会读作辩解。
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(
                    text = stringResource(
                        Res.string.chat_model_provider,
                        catalogProviderNames(catalog).joinToString(stringResource(Res.string.chat_list_separator)),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}
