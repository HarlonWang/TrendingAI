# TrendingAI 客户端

## UI 规范

- **加载指示统一**：所有加载态（页面级 / 列表 / 下拉刷新 / 按钮内 / 列表项 trailing 等内嵌场景）一律用 M3 Expressive 的 `androidx.compose.material3.LoadingIndicator`，**全 app 不用 `CircularProgressIndicator`**。
  - 小尺寸内嵌也用 `LoadingIndicator`：`Modifier.size(24.dp)` 即可（参照首页 topbar 头像登录态、`Feedback/Subscribe` 提交按钮、`Settings` 检查更新）；放在 filled 按钮内时传 `color = MaterialTheme.colorScheme.onPrimary` 保证对比度。
  - 需要 `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`。
  - 下拉刷新用 `PullToRefreshBox` 时**必须显式传 `indicator`**，统一为：
    ```kotlin
    val pullToRefreshState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        state = pullToRefreshState,
        onRefresh = { viewModel.refresh() },
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = pullToRefreshState,
                isRefreshing = uiState.isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
    ) { /* content */ }
    ```
    不传 `indicator` 会回落到默认的 `CircularProgressIndicator`，与全 app 的 `LoadingIndicator` 风格不一致。参照 `Picks/Feed/Trending/Profile` 各 Screen 的写法。
