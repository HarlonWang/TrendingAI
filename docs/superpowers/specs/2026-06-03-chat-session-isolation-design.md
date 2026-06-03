# AI Chat 会话按入口隔离 — 设计文档

日期：2026-06-03
状态：待评审

## 1. 背景与问题

AI Chat 有两个入口：

- **首页**（`HomeScreen` FAB）→ 通用 AI 助手，`onNavigateToChat()` 无参，导航为 `Chat(null)`（`App.kt:50-52`）。
- **GitHub README 详情**（`ReadmeScreen` FAB）→ 携带仓库上下文，导航为 `Chat(ChatContext(title, summary, sourceUrl))`（`App.kt:114-116`）。

现状是两个入口进入后**显示同一个对话、共享同一份历史**，再次进入聊天页也会看到上次的记录混在一起。

### 根因

`ChatScreen` 使用 `viewModel { ChatViewModel(...) }`（`ChatScreen.kt:40`）：

1. **没有传 key**——`viewModel {}` 的默认 key 由 Composable 调用位置决定，两个入口渲染的是同一个 `ChatScreen` 的同一行调用，默认 key 相同。
2. **`NavDisplay` 没有为每个 `NavEntry` 配置独立的 ViewModelStore**（`App.kt:37`，未传 `entryDecorators`），导致 `viewModel {}` 回退到 **Activity 级别**的 `ViewModelStoreOwner`。

两点叠加：ViewModel 挂在 Activity 上、key 又相同 → 两个入口拿到**同一个 ChatViewModel 实例**；且因挂在 Activity 上，离开聊天页不销毁 → 再次进入历史仍在。

## 2. 目标

- 首页通用助手是**一条独立会话线**。
- 每个仓库（owner/repo）是**各自独立的一条会话线**，互不干扰。
- 同一会话线再次进入时**恢复上次对话**（内存级，与现状一致）。
- 不同会话线之间**完全隔离**。

## 3. 非目标（YAGNI）

- **不做**跨 App 重启的落盘持久化（已确认"内存即可"）。进程被杀/重启后历史清空，与现状一致。
- **不改** `Chat` 路由结构、不引入 navigation3 的 per-entry ViewModelStore decorator。
- **不改** iOS（`globalChatScreen` 在 iOS 未注册，入口隐藏，本改动对 iOS 无影响）。
- **不做**会话列表 / 切换 / 删除等会话管理 UI。

## 4. 方案

按 `ChatContext` 计算一个**稳定的 sessionKey**，传给 `viewModel(key = sessionKey)`。Activity 级 ViewModelStore 会按 key 各自缓存一个 `ChatViewModel` 实例，从而天然实现"按会话线隔离 + 再次进入恢复"。

### key 规则

```kotlin
private fun sessionKeyOf(context: ChatContext?): String =
    if (context == null) "chat:general"
    else "chat:" + (context.sourceUrl ?: context.title)
```

- 首页：`context == null` → `"chat:general"`，固定唯一。
- 详情：优先用 `sourceUrl`（仓库 URL，天然唯一稳定）；`sourceUrl` 为空时回退 `title`（即 `owner/repo`，同样唯一）。

### 改动点

仅 `androidLibrary/chat/.../ui/ChatScreen.kt` 一处：

```kotlin
val sessionKey = sessionKeyOf(initialContext)
val viewModel: ChatViewModel = viewModel(key = sessionKey) {
    ChatViewModel(engine, initialContext, initialMessages)
}
```

`App.kt`、`ChatIntegration.kt`、`MainActivity.kt`、`ChatViewModel.kt` **均不改动**。

## 5. 数据流（改动后）

```
首页 FAB        → Chat(null)              → ChatScreen(ctx=null)        → viewModel(key="chat:general")
详情 FAB(repoA) → Chat(ctx: repoA url)    → ChatScreen(ctx=repoA)       → viewModel(key="chat:https://github.com/owner/repoA")
详情 FAB(repoB) → Chat(ctx: repoB url)    → ChatScreen(ctx=repoB)       → viewModel(key="chat:https://github.com/owner/repoB")
```

三个 key 各自映射 Activity ViewModelStore 中一个独立 ChatViewModel 实例。

## 6. 已知取舍

- **内存累积**：浏览过的每个仓库会在 Activity ViewModelStore 中各留一个 ChatViewModel，直到 Activity 销毁。每个实例只持有该会话的消息列表，占用小；App 重启即清空。在当前使用量级下可接受，且符合"内存即可"。后续若需要可加 LRU 上限或落盘，超出本次范围。

## 7. 测试 / 验证

ViewModel scope 行为依赖 Compose runtime，单测覆盖成本高、价值低，**以手动验证为主**：

1. 首页进入聊天，发几条消息 → 返回 → 再进首页聊天：**历史仍在**。
2. 详情 repoA 进入聊天发消息 → 返回 → 进首页聊天：**看不到 repoA 的消息**（隔离）。
3. 详情 repoA 聊天 → 返回 → 详情 repoB 聊天：**两边各自独立**。
4. 详情 repoA 聊天发消息 → 返回 → 再次进入 repoA 详情聊天：**恢复 repoA 的对话**。
5. 杀进程重启 App → 进任意聊天：**历史清空**（符合内存级预期）。

回归：现有 `ChatErrorsTest` 不受影响，应仍通过。
