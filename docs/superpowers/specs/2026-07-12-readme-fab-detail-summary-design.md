# README 详情页 FAB 菜单「一键解读」设计

## 背景与目标

README 详情页右下角当前有一个单独的 `FloatingActionButton`（`AutoAwesome` 星星图标），点击后带 `ChatContext` 跳转到 chat 会话，但只是打开对话，不会自动生成解读——用户进入 chat 后仍需手动点「一键详细解读」chip。

本次目标：把右下角改成**弹出式 FAB 菜单**，提供两个入口——普通「AI 对话」和「一键解读」；点「一键解读」时跳转到 chat 并**自动触发**详细解读，省掉手动点 chip 这一步。

## 交互设计

右下角用 M3 Expressive 的 `FloatingActionButtonMenu` + `ToggleFloatingActionButton` 替换现有单个 FAB（仅当 `globalChatScreen != null` 时显示，与现状一致）：

- **主按钮**（`ToggleFloatingActionButton`）：点击展开/收起菜单。图标要有「菜单」感——收起时 `Icons.Default.Menu`，展开时切换为 `Icons.Default.Close`（M3 标准 toggle 做法）。
- **子项 1「AI 对话」**：跳转 chat，行为等同现在的 FAB（`autoDetailSummary = false`）。
- **子项 2「一键解读」**：跳转 chat 并自动触发详细解读（`autoDetailSummary = true`）。**仅当 `readmeLength ≥ 1500`**（`DetailSummaryPolicy.MIN_README_CHARS`）时渲染——与 chat 里 chip 的显示规则一致；README 未加载完（`readmeLength == null`）也视为不足、隐藏该子项。

组件可用性：`FloatingActionButtonMenu` 属 M3 Expressive API（`@OptIn(ExperimentalMaterial3ExpressiveApi::class)`），需在 JetBrains Compose Multiplatform material3 `1.10.0-alpha05` 上验证可用；若不可用，退化为自定义的展开式 mini FAB（一列 small FAB + 展开动画），交互与文案不变。

## 改动点

共 4 处，最小侵入，不动导航图 / ViewModel / engine / 服务端。

### 1. `ChatContext` 新增意图字段
文件：`chat/.../chat/ChatContext.kt`（`ChatContext` data class）

新增 `val autoDetailSummary: Boolean = false`。该字段随 `Chat(context)` 路由（`App.kt` 的 `data class Chat(val context: ChatContext?)`）自动流转到 chat 页，无需改导航图。默认 `false` 保证既有调用方行为不变。

### 2. `ReadmeScreen` 的 FAB 菜单
文件：`shared/src/commonMain/kotlin/whl/trending/ai/ui/detail/ReadmeScreen.kt`（当前 `floatingActionButton` 槽，约 183–212 行）

- 把单个 `FloatingActionButton` 换成 FAB 菜单。
- 抽出一个构造 `ChatContext` 的 helper（复用现有字段 `title = "$owner/$repo"`、`summary`、`sourceUrl = repoUrl`、`source = "github"`、`externalId = "$owner/$repo"`、`readmeLength`），参数化 `autoDetailSummary`，避免两个子项重复拼装。
- 「AI 对话」子项：`onNavigateToChat(buildContext(autoDetailSummary = false))`。
- 「一键解读」子项：条件 `(readmeLength ?: 0) >= DetailSummaryPolicy.MIN_README_CHARS` 成立才渲染，点击 `onNavigateToChat(buildContext(autoDetailSummary = true))`。
- 若 `DetailSummaryPolicy` / `MIN_README_CHARS` 在 shared commonMain 不可直接引用（跨模块可见性问题），则在 ReadmeScreen 内用同一常量 `1500` 判定，并加注释指向 `DetailSummaryPolicy.MIN_README_CHARS` 保持同步。实现时先确认可见性。

### 3. `ChatScreen` 自动触发
文件：`chat/.../chat/ui/ChatScreen.kt`

新增一次性 `LaunchedEffect`：当 `initialContext?.autoDetailSummary == true` 且 `DetailSummaryPolicy.chipVisible(initialContext, state.messages)` 为真时，调用 `viewModel.sendDetailSummary(detailPrompt)`（`detailPrompt = stringResource(R.string.chat_action_detail_summary)`，与 chip 用同一串）。

防重复触发：`chipVisible` 在已存在成功的 assistant `DETAIL_SUMMARY` 消息时返回 `false`，天然幂等；再叠加 `sendDetailSummary` 自身的 `isSending` / `externalId == null` 守卫。`LaunchedEffect` 的 key 需保证配置变更 / 重组不会重复触发（用 chipVisible 判定即可，触发后状态变化会让条件转 false）。

### 4. 菜单项文案
文件：shared `composeResources`（`shared/src/commonMain/composeResources/values*/strings.xml` 或对应 resources）

新增两条：「AI 对话」/「AI Chat」、「一键解读」/「In-depth read」（en 文案实现时定稿，与现有 `In-depth overview` 风格协调）。「一键解读」子项 label 也可直接复用 chat 的 `chat_action_detail_summary`，但该串在 chat 模块的 res 下，shared 不可跨模块引用，故在 shared 侧新增独立文案。

## 不改动

导航图（`App.kt`）、`ChatViewModel` 逻辑、`ChatEngine` / `ChatApi`、服务端解读接口——现有 `sendDetailSummary → engine.sendDetailSummary(context)` 管线原样复用。

## 边界与风险

- **README 未加载完就点 FAB**：`readmeLength == null` → 「一键解读」子项不显示，用户只能选「AI 对话」；等 README 加载完子项才出现。可接受。
- **短 README（< 1500 字）**：子项不显示，行为与 chip 一致。
- **组件不可用**：退化为自定义 mini FAB 展开，见上。
- **自动触发失败/配额/需登录**：复用现有 `sendDetailSummary` 的错误 / `detail_summary_quota_hit` / `detail_summary_login_required` 处理，无需额外逻辑。

## 验证

按 `TrendingAI/CLAUDE.md` 规范，改完在 Pixel_9_2 模拟器跑 debug 包：
1. 进一个长 README 的 GitHub 项目详情页 → 点右下角 FAB → 菜单展开出现两项。
2. 点「一键解读」→ 自动跳 chat 且立即开始生成详细解读（无需手动点 chip）。
3. 点「AI 对话」→ 跳 chat、不自动解读，可正常输入。
4. 找一个短 README（< 1500 字）项目 → FAB 菜单里「一键解读」不出现。
