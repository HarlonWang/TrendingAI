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

## 版本更新说明（whatsnew）发布流程

App 升级后首启弹的「新版本更新说明」来自 `shared/src/commonMain/composeResources/files/whatsnew.json`（随 APK 打包）。同一份内容也用于拼 GitHub Release 正文。它有两种生成模式，由**内容驱动的开关**决定，无需额外配置：

- **自动（默认）**：仓库里 `whatsnew.json` 保持占位（`version: 0.0.0`、zh/en 空）。直接 `git tag <版本> && git push origin <版本>`，CI（`android_release.yml` 的 Generate What's New 步骤）自动调 AI（gpt-5.4）用 commit 记录生成并打包。
- **手动**：`whatsnew.json` 的 `version` 恰等于本次 tag 且 zh/en 至少一个非空时，CI 检测到「本版已有人工内容」→ **跳过 AI，直接采用**。

### 手动模式：Claude Code 本地执行流程

当我（本地 agent）收到「**这次发布说明走本地**」「**手动写这次更新说明**」「**别让 CI 自动生成 changelog**」等**等价暗示**时，按下面步骤走（本地起草用我自己的 AI，与 CI 的 gpt-5.4 是两套、不共用脚本）：

1. 确定本次将发布的 tag `<版本>`（如 `0.9.0`）。
2. 取上个 tag 到 HEAD 的 commit：
   ```bash
   PREV=$(git tag --sort=-v:refname | grep -E '^[0-9]+\.[0-9]+\.[0-9]+' | grep -v "<版本>" | head -n 1)
   git log "$PREV"..HEAD --no-merges --oneline
   ```
3. 按**面向普通用户**口径起草中英双语更新说明（与 CI prompt 同款要求）：
   - 过滤纯内部改动（构建、CI、打包、签名、依赖升级、重构、测试、文档）及用户无直接感知的能力（统计、埋点、渠道识别、版本分发、监控、日志），只保留用户可感知的新功能、优化、修复；
   - 每条一句话，简洁、不堆技术术语；**中文 3–6 条**，英文为对应翻译。
4. 把草稿逐条交用户调整定稿。
5. 写入 `whatsnew.json`，格式 `{"version": "<版本>", "zh": [...], "en": [...]}`，**`version` 必须等于本次 tag**（否则 CI 判成自动模式、AI 会覆盖你的内容）。
6. `git commit` 后再 `git tag <版本> && git push origin <版本>`，让 tag 指向含手动内容的 commit。
