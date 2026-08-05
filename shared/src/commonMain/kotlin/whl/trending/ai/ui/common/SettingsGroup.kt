package whl.trending.ai.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * 设置项分组：一组连体卡片。
 *
 * 每项一张独立卡片，组内首尾大圆角、中间小圆角、彼此留 4dp 缝，视觉上是一整块被切开的圆角面板。
 * 分组之间靠圆角与 `primary` 色小标题分隔，**不用分隔线**。
 *
 * 用 slot API 而不是「一个 SettingsItem 数据类 + 遍历渲染」：参照仓库那套数据类会随特例不断长字段
 * （Echo 11 个、Rhythm 12 个），而我们的设置项恰恰有 iOS 分支、Switch、DropdownMenu 锚点这些不规则
 * 需求——slot 下它们就是调用处的普通 Compose 代码，组件不必知情。
 *
 * `content` 不是 `@Composable`：它先把每项收集成 lambda（同 `LazyListScope.item` 的做法），
 * 收集完才知道总数，进而给每项分配正确的圆角。方法名叫 `settingsItem` 而不是 `item`，
 * 是为了在 `LazyColumn.item { }` 内部调用时不与 `LazyListScope.item` 撞名。
 *
 * ```
 * SettingsGroup(title = "个性化", modifier = Modifier.padding(horizontal = 16.dp)) {
 *     item(
 *         icon = Icons.Default.Palette,
 *         title = { Text("外观") },
 *         trailing = { Text(themeModeText(themeMode)) },
 *         onClick = { onNavigateToAppearance() },
 *     )
 * }
 * ```
 */
@Composable
fun SettingsGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: SettingsGroupScope.() -> Unit,
) {
    val entries = SettingsGroupScope().apply(content).entries
    Column(modifier = modifier.fillMaxWidth()) {
        title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(ItemGap)) {
            entries.forEachIndexed { index, entry ->
                entry(groupItemShape(index, entries.size))
            }
        }
    }
}

/** 收集设置项。见 [SettingsGroup] 的说明——这里收的是 lambda，渲染发生在拿到总数之后。 */
class SettingsGroupScope internal constructor() {
    internal val entries = mutableListOf<@Composable (Shape) -> Unit>()

    /**
     * 一个设置项。[trailing] 放开关、当前值、下拉菜单锚点等；整行点击走 [onClick]。
     * 图标会被放进 40dp 的圆形容器里（与首页底栏选中药丸同一对 token）。
     */
    fun settingsItem(
        icon: ImageVector? = null,
        title: @Composable () -> Unit,
        description: (@Composable () -> Unit)? = null,
        enabled: Boolean = true,
        onClick: (() -> Unit)? = null,
        trailing: (@Composable () -> Unit)? = null,
    ) {
        entries += { shape ->
            SettingsItemRow(
                shape = shape,
                icon = icon,
                title = title,
                description = description,
                enabled = enabled,
                onClick = onClick,
                trailing = trailing,
            )
        }
    }
}

@Composable
private fun SettingsItemRow(
    shape: Shape,
    icon: ImageVector?,
    title: @Composable () -> Unit,
    description: (@Composable () -> Unit)?,
    enabled: Boolean,
    onClick: (() -> Unit)?,
    trailing: (@Composable () -> Unit)?,
) {
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = DisabledAlpha)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier
                )
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let {
                Surface(
                    modifier = Modifier.size(IconContainerSize),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = it,
                            contentDescription = null,
                            tint = if (enabled) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = DisabledAlpha)
                            },
                            modifier = Modifier.size(IconSize),
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                ProvideTextStyle(MaterialTheme.typography.titleMedium.copy(color = contentColor)) {
                    title()
                }
                description?.let {
                    Spacer(Modifier.height(2.dp))
                    ProvideTextStyle(
                        MaterialTheme.typography.bodyMedium.copy(
                            color = if (enabled) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = DisabledAlpha)
                            },
                        )
                    ) { it() }
                }
            }

            trailing?.let {
                Spacer(Modifier.width(8.dp))
                it()
            }
        }
    }
}

/** 首项顶部大圆角、末项底部大圆角、中间一律小圆角；只有一项时四角全大。 */
private fun groupItemShape(index: Int, count: Int): Shape {
    val top = if (index == 0) LargeCorner else SmallCorner
    val bottom = if (index == count - 1) LargeCorner else SmallCorner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

private val LargeCorner = 24.dp
private val SmallCorner = 6.dp
private val ItemGap = 4.dp
private val IconContainerSize = 40.dp
private val IconSize = 24.dp
private const val DisabledAlpha = 0.38f
