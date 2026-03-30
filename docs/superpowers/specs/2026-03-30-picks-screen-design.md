# Picks Screen 设计

## 概述

为 TrendingAI 客户端的 Picks tab 接入 `/api/picks` 数据，展示每日 AI 精选内容。三段式纵向滚动布局：深度解读（Deep Dive）→ 争议话题（Controversy）→ Top 5 速览（Speed Read）。

## API

```
GET /api/picks
```

不传任何参数，服务端默认返回当天数据、中文摘要。

### Response 结构

```json
{
  "success": true,
  "metadata": { "date": "2026-03-30" },
  "speedRead": [PickItem],
  "deepDive": [PickItem],
  "controversy": [PickItem]
}
```

### PickItem

```json
{
  "rank": 1,
  "source": "github",
  "title": "...",
  "url": "https://github.com/owner/repo",
  "description": "...",
  "score": 2300,
  "aiScore": 9.2,
  "sourceLabel": "GitHub",
  "alsoOn": ["hackernews"],
  "summary": "...",
  "analysis": {
    "core": "一句话核心要点",
    "why_important": "为何重要",
    "community_voice": {
      "positive": "支持方观点",
      "negative": "反对方观点"
    },
    "action": "适用场景（仅 deep_dive）",
    "alternatives": "替代品（仅 deep_dive）",
    "terms": ["关键词"]
  }
}
```

- `analysis` 仅 deepDive 和 controversy 有，speedRead 为 null
- `action` 和 `alternatives` 仅 deepDive 有

## 数据模型（Kotlin）

### PicksResponse

```kotlin
data class PicksResponse(
    val success: Boolean,
    val metadata: PicksMetadata,
    val speedRead: List<PickItem>,
    val deepDive: List<PickItem>,
    val controversy: List<PickItem>
)

data class PicksMetadata(val date: String)
```

### PickItem

```kotlin
data class PickItem(
    val rank: Int,
    val source: String,
    val title: String,
    val url: String,
    val description: String?,
    val score: Int,
    val aiScore: Double,
    val sourceLabel: String,
    val alsoOn: List<String>,
    val summary: String?,
    val analysis: PickAnalysis?
)
```

### PickAnalysis

```kotlin
data class PickAnalysis(
    val core: String,
    @SerialName("why_important")
    val whyImportant: String,
    @SerialName("community_voice")
    val communityVoice: CommunityVoice,
    val action: String?,
    val alternatives: String?,
    val terms: List<String>?
)

data class CommunityVoice(
    val positive: String,
    val negative: String
)
```

## API 层

TrendingApi 新增：

```kotlin
suspend fun fetchPicks(): PicksResponse
```

TrendingRepository 新增：

```kotlin
suspend fun getPicks(): PicksResponse
```

## UI 架构

### 文件结构

```
ui/picks/
├── PicksScreen.kt        # 改造现有占位，三段式列表
└── PicksViewModel.kt     # 新增
```

### PicksViewModel

```kotlin
data class PicksUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val picks: PicksResponse? = null
)
```

- `init` 时自动调用 `fetchPicks()`
- 暴露 `retry()` 方法供错误重试
- 不支持下拉刷新（Picks 一天只更新一次）

### PicksScreen 布局

三段式纵向滚动，使用 LazyColumn：

```
LazyColumn {
    // Deep Dive 段
    stickyHeader { SectionHeader("深度解读") }
    items(deepDive) { DeepDiveCard(item) }

    // Controversy 段
    stickyHeader { SectionHeader("争议话题") }
    items(controversy) { ControversyCard(item) }

    // Speed Read 段
    stickyHeader { SectionHeader("Top 5 速览") }
    items(speedRead) { SpeedReadItem(item) }
}
```

加载/错误状态：LoadingIndicator 居中 + 错误信息 + 重试按钮（复用 Trending 的模式，无下拉刷新）。

### 卡片组件

**DeepDiveCard：**
- 左边框：紫色（`#bb86fc` / M3 primary）
- 内容：源标签（右上角）+ 标题 + `core` + `whyImportant` + 正反方观点（👍/👎）
- 底部标签：`action`（适用场景）+ `alternatives`（替代品）
- 圆角卡片背景

**ControversyCard：**
- 左边框：红色强调
- 内容：源标签 + 标题 + `core` + 正反方观点
- 无 `action`/`alternatives`

**SpeedReadItem：**
- 紧凑列表样式：序号（rank）+ 标题 + 分数
- 分数格式：GitHub 用 ★ + 格式化数字，HN/PH 用 ▲ + 数字
- 分隔线分隔

### 源标签样式

| Source | 背景色 | 文字色 |
|--------|--------|--------|
| GitHub | 绿色（#1b5e20） | #a5d6a7 |
| Hacker News | 橙色（#e65100） | #ffcc80 |
| Product Hunt | 红棕色（#da552f） | white |

### 段落标题样式

每段标题包含图标和文字：
- 深度解读：紫色文字
- 争议话题：红色文字
- Top 5 速览：青色文字

## 点击行为

- **GitHub 来源**：解析 url 提取 owner/repo，跳转现有 ReadmeScreen
- **HN / PH 来源**：调用 `openUrl(url)` 打开系统浏览器

## HomeScreen 联动

- HomeScreen 中创建 PicksViewModel，传入 PicksScreen
- PicksTopBar 不变（标题 "Picks" + 设置按钮）
- PicksScreen 签名：

```kotlin
@Composable
fun PicksScreen(
    onNavigateToDetail: (owner: String, repo: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PicksViewModel
)
```

- HomeScreen 需要将 `onNavigateToDetail` 传给 PicksScreen
- 日期切换：不支持，只显示当天

## 不做的事

- 不传 `summary_lang` 参数
- 不做日期选择器
- 不做下拉刷新
- 不做 aiScore 展示（卡片中不显示分数）
- 不做 `alsoOn` 标签展示（V1 不展示跨源信息）
