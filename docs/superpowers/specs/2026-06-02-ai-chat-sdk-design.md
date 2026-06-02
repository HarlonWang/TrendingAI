# AI Chat SDK · 设计文档

| 项 | 值 |
|----|----|
| 创建日期 | 2026-06-02 |
| 状态 | **草案** |
| 涉及仓库 | `TrendingAI`（客户端，KMP + Compose Multiplatform） |
| 触发动因 | 为 App 增加 AI 聊天能力，并封装为可在本 App 内复用的纯 Android Compose SDK |
| 关键依赖 | [commonmark-java](https://github.com/commonmark/commonmark-java)（Markdown 解析）、Ktor（HTTP）、Compose Material 3 |

---

## 1. 背景与目标

### 1.1 现状

- App 为 KMP + Compose Multiplatform，UI 主体在 `:shared` commonMain（CMP）
- 已有 Android-only library module 范式：`androidLibrary/updater`，通过 `:shared` 定义接口 + 全局变量做依赖反转（`UpdateChecker` / `globalUpdateChecker` / `NoOpUpdateChecker`），`androidApp` 用 `UpdateWrapper { App() }` 注册
- 后端 `api.trendingai.cn` 已存在，AI 摘要统一走 ChatGPT
- README 详情用 WebView 静态渲染（整页、内容固定，体验良好）

### 1.2 调研结论（决定渲染路线）

逆向 Claude Code Android App（v1.260416.20）确认其聊天渲染架构：

- **聊天正文全程原生 Compose 渲染，未使用 WebView**
- Markdown 解析用 **commonmark-java** → 转自家 AST → 原生 Compose 渲染
- 代码块语法高亮：用 **AndroidX JavaScriptEngine**（无头 V8 沙箱，非 WebView）跑 highlight.js + 自定义 `PositionEmitter`，JS 只回传 token 区间 `[start, end, type]`，原生构建 `SpannableString[]` 上色，并有「失败降级纯文本」容错
- 证据：`assets/highlight.min.js`、`assets/token-highlight.js`、`androidx.javascriptengine.*`、`highlightTokens(code, lang)`、`org/commonmark/...`

**启示**：聊天列表（`LazyColumn`）场景下原生渲染才是正解；WebView 适合 README 那种整页静态内容。之前用 mikepenz 渲染器觉得慢，问题在库实现而非「原生」本身。本 SDK 采用 commonmark-java 原生渲染。

### 1.3 目标

- 全屏 `ChatScreen`，作为导航目的地
- 支持两种入口：通用 AI 助手（无上下文）+ 项目/资讯解读（带初始上下文）
- ChatGPT 返回的 Markdown 原生渲染：标题、列表、代码块、表格、链接、加粗斜体、引用、行内代码
- **内置离线 Demo**：示例数据 + 假引擎，无需接 API 即可完整体验展示与交互
- 封装为独立 module，仅本 App 内复用，骨架照搬 `updater`

### 1.4 非目标（YAGNI 显式声明）

- ❌ 流式 SSE 输出（本期一次性返回，打字机后续再加）
- ❌ JS 引擎（JavaScriptEngine）专业多语言高亮（本期轻量正则高亮，后续按需补 Claude 同款 A 方案）
- ❌ 会话持久化 / 多会话 / 历史列表（本期内存级单会话）
- ❌ 后端端点契约的最终定稿（客户端先按接口编码，后端延后讨论）
- ❌ KMP 跨平台（本期纯 Android；commonmark-java 为 JVM 库）
- ❌ 图片、复杂表格的特殊优化（先满足常规渲染）

---

## 2. 关键决策

| 决策 | 选择 | 备选 | 原因 |
|------|------|------|------|
| 复用边界 | 仅本 App 内复用 | 跨 App / 开源 SDK | YAGNI，本期聚焦落地 |
| 模块骨架 | 照搬 `androidLibrary/updater` | 新设计 | 已验证范式，降低风险 |
| 聊天形态 | 全屏 `ChatScreen` 导航目的地 | 底部 Sheet / 两者 | 最常规，通用入口与详情追问都跳同一页 |
| 聊天用途 | 通用助手 + 上下文解读 | 仅其一 | 接口支持可选初始上下文，最灵活 |
| 输出方式 | 一次性返回（非流式） | SSE 流式 | 前后端成本低，先跑通核心体验 |
| Markdown 渲染 | commonmark-java 原生渲染 + 轻量高亮 | mikepenz（慢）/ WebView / JS 引擎 | 性能可控、Claude 验证过的正确路线；避开 JS 沙箱复杂度 |
| 代码高亮 | 轻量正则高亮（少数语言）+ 纯文本兜底 | JavaScriptEngine + highlight.js | 省工程复杂度，后续可升级 |
| 会话历史 | 内存级单会话 | 持久化 / 多会话 | YAGNI |
| 接入方式 | `:shared` 全局 slot 注入 + `androidApp` 注册 | 独立 Activity | 与 `updater` 一致，导航统一 |
| 引擎抽象 | `ChatEngine` 接口，UI 注入 | 直接调 API | Demo 注入假引擎、正式注入 API，UI 零改动 |

---

## 3. 架构与文件布局

### 3.1 `:shared` commonMain 新增

`shared/src/commonMain/kotlin/whl/trending/ai/chat/ChatIntegration.kt`：

```kotlin
package whl.trending.ai.chat

import androidx.compose.runtime.Composable

/** 进入聊天时可携带的初始上下文（项目/HN/PH 条目）；通用助手入口传 null */
data class ChatContext(
    val title: String,
    val summary: String?,
    val sourceUrl: String?,
)

/** Android-only ChatScreen 通过全局 slot 注入 CMP 导航；未注册时为 null（NoOp） */
var globalChatScreen: (@Composable (context: ChatContext?, onBack: () -> Unit) -> Unit)? = null
```

- shared 导航图新增 `Chat` 目的地，其内容调用 `globalChatScreen?.invoke(ctx, onBack)`
- 入口按钮：首页/设置「AI 助手」（ctx=null）、项目/HN/PH 详情「AI 解读」（带 ctx）→ 导航到 `Chat`
- 初始上下文的传递：随导航参数携带，或在 `ChatIntegration` 暂存 `pendingChatContext` 并在进入时消费（本期单会话，二选简单者）

### 3.2 `androidApp` 注册（仿 `UpdateWrapper`）

`androidApp/src/.../ChatRegistration`：在合适时机设置
```kotlin
globalChatScreen = { ctx, onBack -> ChatScreen(initialContext = ctx, onBack = onBack) }
```

### 3.3 新模块 `androidLibrary/chat`

```
androidLibrary/chat/
├── build.gradle.kts                # 照搬 updater：androidLibrary + composeCompiler + serialization
├── src/main/
│   ├── kotlin/whl/trending/chat/
│   │   ├── engine/
│   │   │   ├── ChatEngine.kt        # interface { suspend fun send(history, context): String }
│   │   │   └── ChatApi.kt           # Ktor 实现，POST → api.trendingai.cn（端点契约后端再定）
│   │   ├── model/
│   │   │   ├── ChatMessage.kt       # id, role(USER/ASSISTANT), content, status(Sending/Done/Error)
│   │   │   └── ChatUiState.kt       # messages, input, isSending, error
│   │   ├── ChatViewModel.kt         # StateFlow<ChatUiState>；注入 ChatEngine；send()/retry()/updateInput()
│   │   ├── markdown/
│   │   │   ├── MarkdownParser.kt    # commonmark-java Parser 封装 → Node
│   │   │   ├── MarkdownText.kt      # 遍历 Node → 原生 Compose；inline 拼 AnnotatedString
│   │   │   └── CodeBlock.kt         # 等宽 + 横向滚动 + 语言标签 + 复制；轻量正则高亮
│   │   ├── ui/
│   │   │   ├── ChatScreen.kt        # Scaffold(topBar 返回) + MessageList + ChatInputBar
│   │   │   ├── MessageList.kt       # LazyColumn，新消息自动滚底，key = message.id
│   │   │   ├── MessageItem.kt       # 用户气泡(右)/助手 MarkdownText(左)；SelectionContainer；Error+重试
│   │   │   ├── ChatInputBar.kt      # 输入框 + 发送，发送中禁用
│   │   │   └── TypingIndicator.kt   # 非流式「思考中」动画
│   │   ├── sample/
│   │   │   ├── SampleData.kt        # 覆盖全 Markdown 元素的示例消息
│   │   │   └── FakeChatEngine.kt    # 假引擎：发送后回预置 Markdown（含模拟延迟）
│   │   └── ChatDemoActivity.kt      # 独立可启动 Demo 入口，托管 demo 模式 ChatScreen
│   ├── res/values/strings.xml
│   ├── res/values-zh/strings.xml
│   └── AndroidManifest.xml          # 声明 ChatDemoActivity（debug 可启动）
```

`settings.gradle.kts` 增加 `include(":androidLibrary:chat")`。

### 3.4 依赖（全走 version catalog）

`gradle/libs.versions.toml` 新增：
- `[versions]`：`commonmark`
- `[libraries]`：`commonmark`（`org.commonmark:commonmark`，按需加 `commonmark-ext-gfm-tables` 支持表格）

模块 `build.gradle.kts` 依赖：`project(":shared")`、`libs.ktor.client.okhttp` / `contentNegotiation` / `serialization.kotlinxJson`、`libs.kotlinx.serialization.json`、`libs.compose.material3` / `runtime`、`libs.androidx.lifecycle.viewmodelCompose`、`libs.commonmark`（+ tables）。

---

## 4. 引擎抽象（Demo 与正式同一套 UI）

```kotlin
interface ChatEngine {
    suspend fun send(history: List<ChatMessage>, context: ChatContext?): String
}
```

- `FakeChatEngine`：忽略入参，`delay` 一段后返回 `SampleData` 中的预置 Markdown，驱动完整「输入 → 发送 → 思考中 → 渲染」链路
- `ChatApi`：Ktor 实现，POST 历史消息 + 可选上下文到 `api.trendingai.cn`，解析返回的 Markdown 字符串
- `ChatViewModel(engine: ChatEngine)`：Demo 注入 `FakeChatEngine`，正式注入 `ChatApi`，UI 零改动

---

## 5. 数据流

```
点「AI 助手」(ctx=null) / 详情「AI 解读」(带 ctx)
  → shared 导航到 Chat 目的地 → globalChatScreen(ctx, onBack)
    → ChatScreen 持有 ChatViewModel（带 ctx 时作初始上下文）
      → 用户发送 → ViewModel.send() → ChatEngine.send()
          ├─ 正式：ChatApi POST → api.trendingai.cn → ChatGPT
          └─ Demo：FakeChatEngine 返回预置 Markdown
        → ChatMessage(status=Done) → MarkdownText 用 commonmark AST 渲染
```

---

## 6. Markdown 渲染细节

- `MarkdownParser`：commonmark-java `Parser`（启用 GFM tables 扩展），`parse(text): Node`
- `MarkdownText`：递归遍历 commonmark `Node`：
  - block：`Heading` / `Paragraph` / `BulletList` / `OrderedList` / `BlockQuote` / `FencedCodeBlock`(→ `CodeBlock`) / `ThematicBreak` / `Table`
  - inline：`Text` / `Emphasis` / `StrongEmphasis` / `Code` / `Link` 拼成 `AnnotatedString`
  - 链接点击：`LocalUriHandler` 拉起浏览器
- `CodeBlock`：等宽字体、横向滚动、语言标签、复制按钮；轻量正则高亮覆盖少数常见语言（kotlin/js/json…），未命中走纯文本

---

## 7. 性能要点（直击 mikepenz 慢的痛点）

- **每条消息只解析一次**：`remember(message.content)` 缓存 commonmark AST；非流式下 content 一旦 `Done` 即终态，全程解析一次，滚动不重解析
- `LazyColumn` 用 `message.id` 做稳定 key，避免重组重建
- 全原生 AnnotatedString / composable，无 View interop、无 WebView 内存开销

---

## 8. 错误处理

- `ChatEngine.send()` 失败 → 该条 `status=Error` + 重试按钮
- Ktor `HttpTimeout` 放宽到 ~60s（非流式等待较久）
- 网络/解析异常统一 catch，UI inline 错误提示，不崩溃

---

## 9. 测试

- `ChatViewModel`：注入 `FakeChatEngine` 测对话推进 / 错误 / 重试 / 输入状态
- `MarkdownParser`：解析映射单测（标题 / 列表 / 代码块 / 链接 / 加粗 / 表格）
- Compose UI：`@Preview`（`MessageItem` / `CodeBlock` / `TypingIndicator`）+ `ChatDemoActivity` 手测

---

## 10. 交付边界

- ✅ 本期：全屏 ChatScreen、通用 + 上下文入口、非流式、commonmark 原生渲染 + 轻量高亮、内存级单会话、**离线 Demo（示例数据 + 假引擎 + 独立启动入口）**
- ⬜ 后续：流式 SSE、JavaScriptEngine 专业高亮、会话持久化 / 历史、后端端点契约定稿

---

## 11. 实施顺序（先展示交互、后接 API）

1. 模块骨架 + version catalog 依赖 + `ChatEngine` 接口 + 数据模型
2. `MarkdownParser` + `MarkdownText` + `CodeBlock`（渲染核心）
3. `ui/` 组件 + `ChatScreen` + `@Preview`
4. `SampleData` + `FakeChatEngine` + `ChatDemoActivity` → **此处即可重点验收展示与交互效果**
5. `ChatViewModel` 接 `FakeChatEngine` 跑通完整交互
6. `:shared` 接口 slot + 入口按钮 + `androidApp` 注册
7. `ChatApi` 实现（待后端端点契约确定后接入）
