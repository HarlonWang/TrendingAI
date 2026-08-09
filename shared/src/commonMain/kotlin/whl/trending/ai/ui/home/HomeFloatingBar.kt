package whl.trending.ai.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.chat_title

/**
 * 悬浮底栏胶囊本体的高度，不含系统导航栏 inset 与外边距。
 * 对齐 Echo 的 `FloatingToolbarHeight`（constants/Dimensions.kt）。
 */
val FloatingBarHeight: Dp = 72.dp

/** 胶囊与屏幕底边、与内容之间的呼吸位。对齐 Echo 的 `FloatingToolbarBottomPadding`。 */
val FloatingBarBottomMargin: Dp = 12.dp

/** 胶囊最大宽度，平板/大屏上不至于拉成一整条。对齐 Echo。 */
private val BarMaxWidth: Dp = 480.dp

/**
 * 底栏一项。图标备实心/描边两态，选中切实心——与 Echo 的 `Screens.iconIdActive/Inactive` 同构。
 */
internal data class HomeBarItem(
    val key: HomeTab,
    val iconSelected: ImageVector,
    val iconUnselected: ImageVector,
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
)

/**
 * 首页悬浮底栏：M3 Expressive 的胶囊工具栏 + 右侧一颗独立的 AI 对话 FAB。
 *
 * 骨架照搬 Echo Music 的 `FloatingNavigationToolbar`：外层 [BoxWithConstraints] 撑满并居中，
 * 工具栏用 `widthIn(max)` 收住宽度，FAB 放在工具栏自带的槽位里。
 * 选中态是一块跟着选中项滑动的药丸（见 [SlidingPillItems]）；只有图标、没有文字标签
 * ——Echo 那边 `showSelectedLabels` 写死为 false。
 *
 * FAB 槽位的内容是有意偏离 Echo 的产品决策（1.1 起）：Chat 是动作入口而非 tab 落点
 * （点击推全屏页、从不进选中态），按 M3 浮动工具栏语义应占主动作槽位——参照 Gmail
 * 底栏的写信 FAB；此前放这里的「⋯」溢出菜单（设置/关于）已删，两者在设置页内都有路径。
 *
 * [onOpenChat] 为 null 时（iOS 未接入 chat）不渲染 FAB，底栏退化为纯胶囊。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun HomeFloatingBar(
    items: List<HomeBarItem>,
    onOpenChat: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        val colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
            toolbarContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        )
        if (onOpenChat != null) {
            HorizontalFloatingToolbar(
                expanded = true,
                floatingActionButton = { ChatFab(onClick = onOpenChat) },
                modifier = Modifier.widthIn(max = BarMaxWidth),
                colors = colors,
                animationSpec = FloatingToolbarDefaults.animationSpec(),
            ) {
                SlidingPillItems(items)
            }
        } else {
            HorizontalFloatingToolbar(
                expanded = true,
                modifier = Modifier.widthIn(max = BarMaxWidth),
                colors = colors,
            ) {
                SlidingPillItems(items)
            }
        }
    }
}

/**
 * 图标行 + 底下那块滑动药丸。药丸单独占一层 [Modifier.matchParentSize]，不参与
 * [IntrinsicSize] 测量；位置与宽度取自各项 [onGloballyPositioned] 报回来的实测值。
 */
@Composable
private fun SlidingPillItems(items: List<HomeBarItem>) {
    val density = LocalDensity.current
    val itemWidths = remember { mutableStateMapOf<HomeTab, Dp>() }
    val itemPositions = remember { mutableStateMapOf<HomeTab, Dp>() }

    val active = items.firstOrNull { it.selected }
    val targetWidth = active?.let { itemWidths[it.key] } ?: 0.dp
    val targetPosition = active?.let { itemPositions[it.key] } ?: 0.dp

    val slidingPillWidth by animateDpAsState(
        targetValue = targetWidth,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "pillWidth",
    )
    val slidingPillOffset by animateDpAsState(
        targetValue = targetPosition,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "pillOffset",
    )

    Box(modifier = Modifier.height(IntrinsicSize.Min)) {
        Box(modifier = Modifier.matchParentSize()) {
            if (targetWidth > 0.dp) {
                Box(
                    modifier = Modifier
                        .offset(x = slidingPillOffset)
                        .width(slidingPillWidth)
                        .fillMaxHeight()
                        .background(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(24.dp),
                        )
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            items.forEach { item ->
                BarItem(
                    item = item,
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        with(density) {
                            itemWidths[item.key] = coordinates.size.width.toDp()
                            itemPositions[item.key] = coordinates.positionInParent().x.toDp()
                        }
                    },
                )
            }
        }
    }
}

/**
 * 单项：只有图标。宽度由内容撑开（下限 48dp），选中时图标 Crossfade 到实心态并放大、
 * 颜色渐变，按下时整体缩一下。各段动画参数与 Echo 的 `FloatingNavigationToolbarItem` 相同。
 */
@Composable
private fun BarItem(item: HomeBarItem, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(24.dp)
    val transition = updateTransition(targetState = item.selected, label = "navItem_${item.key.name}")

    val contentColor by transition.animateColor(
        transitionSpec = { spring(stiffness = Spring.StiffnessMedium) },
        label = "contentColor",
    ) { isSelected ->
        if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant
    }

    val iconScale by transition.animateFloat(
        transitionSpec = {
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow,
            )
        },
        label = "iconScale",
    ) { isSelected -> if (isSelected) 1.12f else 1.0f }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.91f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "pressScale",
    )

    Row(
        modifier = modifier
            .scale(pressScale)
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Tab,
                onClick = item.onClick,
            )
            .widthIn(min = 48.dp)
            // Echo 在展示文字标签时把水平 padding 撑到 16dp；标签关掉后它取的就是这个 12dp
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Crossfade(
            targetState = item.selected,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
            label = "iconCrossfade",
            modifier = Modifier.scale(iconScale),
        ) { isSelected ->
            Icon(
                imageVector = if (isSelected) item.iconSelected else item.iconUnselected,
                contentDescription = item.label,
                tint = contentColor,
            )
        }
    }
}

/**
 * AI 对话入口 FAB：占浮动工具栏的主动作槽位（缘由见 [HomeFloatingBar] 的 KDoc）。
 * 图标沿用改版前 chat 入口的 ✨（AutoAwesome）；底色与胶囊同为 surfaceContainer，
 * 不用 vibrant 强调色——FAB 靠悬浮位置区分即可，撞色反而比胶囊抢眼。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChatFab(onClick: () -> Unit) {
    FloatingToolbarDefaults.VibrantFloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        // Echo 用的是默认形状——androidx 版默认就是圆；CMP 这版默认是圆角方形，
        // 显式传 CircleShape 才能得到与 Echo 相同的外观。
        shape = CircleShape,
    ) {
        Icon(Icons.Default.AutoAwesome, contentDescription = stringResource(Res.string.chat_title))
    }
}
