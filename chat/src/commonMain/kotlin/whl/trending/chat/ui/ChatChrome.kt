package whl.trending.chat.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp

/**
 * SDK 自带的页面骨架与下拉菜单，规格对齐宿主 TrendingAI 的统一件（TrendingScaffold /
 * TrendingDropdownMenu）：顶栏恒定 surfaceContainer、pinned 不折叠、菜单 24dp 大圆角。
 * SDK 不依赖宿主，主题经宿主 MaterialTheme 继承。恒定底色的取舍见 TrendingAI/CLAUDE.md 。
 */
@OptIn(ExperimentalMaterial3Api::class)
internal val LocalChatTopAppBarScrollBehavior = compositionLocalOf<TopAppBarScrollBehavior?> { null }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    CompositionLocalProvider(LocalChatTopAppBarScrollBehavior provides scrollBehavior) {
        Scaffold(
            modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = topBar,
            bottomBar = bottomBar,
            content = content,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    // 居中：标题槽放模型选择器（单模型时回落文字标题），两侧图标由组件自动避让
    CenterAlignedTopAppBar(
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        scrollBehavior = LocalChatTopAppBarScrollBehavior.current,
    )
}

@Composable
internal fun ChatDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        containerColor = MenuDefaults.containerColor,
        tonalElevation = MenuDefaults.TonalElevation,
        content = content,
    )
}

/**
 * chat 模块的底部浮层，规格镜像宿主 app 的 `TrendingBottomSheet`（chat 是独立 SDK，不能依赖 shared）：
 * `titleLarge` 标题、24dp 水平边距、导航栏避让 + 16dp 底部留白。内容区不自带滚动。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatBottomSheet(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    titleTrailing: (@Composable () -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    // 全展开：内容是短列表，半展开会把末项与页脚裁在折叠线下
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                titleTrailing?.invoke()
            }
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}
