package whl.trending.ai.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.about_us
import trendingai.shared.generated.resources.more_label
import trendingai.shared.generated.resources.settings

/** 悬浮底栏胶囊本体的高度，不含系统导航栏 inset 与外边距。 */
val FloatingBarHeight: Dp = 64.dp

/** 胶囊与屏幕底边、与内容之间的呼吸位。 */
val FloatingBarBottomMargin: Dp = 12.dp

/** 底栏一项。[selected] 为 false 且点击后不改变选中态的项（AI 对话）也走这里，见 [HomeTab.Chat]。 */
internal data class HomeBarItem(
    val key: HomeTab,
    val icon: ImageVector,
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
)

/**
 * 首页悬浮底栏：M3 Expressive 的胶囊工具栏 + 右侧一颗独立的「⋯」FAB。
 *
 * 选中态不是给单项加底色，而是一块跟着选中项滑动的药丸——位置与宽度都用 spring 追过去，
 * 切 tab 时能看出「同一块高亮移过去了」，而不是这边灭那边亮。选中项额外展开文字标签，
 * 未选中只留图标，四项加一颗 FAB 才塞得进一行。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun HomeFloatingBar(
    items: List<HomeBarItem>,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 不用工具栏自带的 FAB 槽位：那个槽位会把胶囊撑满整行、项挤在左边留一大片空白。
    // 自己排成「胶囊（按内容宽度）+ 间隙 + 独立 FAB」，整体居中。
    Row(
        // 必须定高：不定的话 Row 会撑满父 Box，FAB 跟着拉成一根竖条、胶囊被垂直居中
        modifier = modifier.height(FloatingBarHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HorizontalFloatingToolbar(
            expanded = true,
            modifier = Modifier.fillMaxHeight(),
        ) {
            SlidingPillItems(items)
        }
        OverflowFab(onOpenSettings = onOpenSettings, onOpenAbout = onOpenAbout)
    }
}

/** 图标行 + 底下那块滑动药丸。药丸画在 Row 之下同一个 Box 里，靠测量到的位置/宽度定位。 */
@Composable
private fun SlidingPillItems(items: List<HomeBarItem>) {
    val density = LocalDensity.current
    val widths = remember { mutableStateMapOf<HomeTab, Dp>() }
    val positions = remember { mutableStateMapOf<HomeTab, Dp>() }

    val active = items.firstOrNull { it.selected }
    val targetWidth = active?.let { widths[it.key] } ?: 0.dp
    val targetOffset = active?.let { positions[it.key] } ?: 0.dp

    val pillSpring = spring<Dp>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
    val pillWidth by animateDpAsState(targetWidth, pillSpring, label = "pillWidth")
    val pillOffset by animateDpAsState(targetOffset, pillSpring, label = "pillOffset")

    Box(modifier = Modifier.height(IntrinsicSize.Min)) {
        if (targetWidth > 0.dp) {
            Box(
                modifier = Modifier
                    .offset(x = pillOffset)
                    .width(pillWidth)
                    .fillMaxHeight()
                    .padding(vertical = 6.dp)
                    .background(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = CircleShape,
                    )
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            items.forEach { item ->
                BarItem(
                    item = item,
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        with(density) {
                            widths[item.key] = coordinates.size.width.toDp()
                            positions[item.key] = coordinates.positionInParent().x.toDp()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun BarItem(item: HomeBarItem, modifier: Modifier = Modifier) {
    val contentColor = if (item.selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = modifier
            .height(FloatingBarHeight)
            .selectable(
                selected = item.selected,
                role = Role.Tab,
                // 药丸自己就是选中反馈，再叠一圈 ripple 会糊成一团
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = item.onClick,
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Icon(item.icon, contentDescription = item.label)
            // 只有选中项展开文字：四项都带标签会撑爆一行，全不带又认不出来
            AnimatedVisibility(
                visible = item.selected,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally(),
            ) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                )
            }
        }
    }
}

/** 「⋯」扩展菜单：设置 / 关于我们。图标各自套一层圆形 tonal 底，与 M3 菜单的分量匹配。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OverflowFab(onOpenSettings: () -> Unit, onOpenAbout: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Box {
        androidx.compose.material3.FloatingToolbarDefaults.VibrantFloatingActionButton(
            onClick = { expanded = !expanded },
            // 默认是圆角方形；这里要的是一颗独立的圆，和胶囊区分开
            shape = CircleShape,
            modifier = Modifier.size(FloatingBarHeight),
        ) {
            Icon(Icons.Default.MoreHoriz, contentDescription = stringResource(Res.string.more_label))
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = MaterialTheme.shapes.large,
        ) {
            OverflowMenuItem(
                text = stringResource(Res.string.settings),
                icon = Icons.Default.Settings,
                onClick = {
                    expanded = false
                    onOpenSettings()
                },
            )
            OverflowMenuItem(
                text = stringResource(Res.string.about_us),
                icon = Icons.Default.Info,
                onClick = {
                    expanded = false
                    onOpenAbout()
                },
            )
        }
    }
}

@Composable
private fun OverflowMenuItem(text: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text) },
        onClick = onClick,
        leadingIcon = {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null)
                }
            }
        },
        colors = MenuDefaults.itemColors(),
    )
}
