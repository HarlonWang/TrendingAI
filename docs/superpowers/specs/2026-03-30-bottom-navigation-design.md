# M3 NavigationBar 底部导航栏设计

## 概述

为 TrendingAI 客户端新增 Material 3 NavigationBar 底部导航栏，包含两个 tab：Trending（现有 GitHub 内容）和精选（Picks，占位）。

## 设计决策

### 架构模式：Container 模式

底部 tab 是 HomeScreen 容器内的子导航，不是顶层路由。这是 Google 官方推荐的 Material 3 底部导航模式。

```
App.kt (NavDisplay backStack)
 └─ Home → HomeScreen (Scaffold + NavigationBar + tab 切换)
     ├─ TrendingScreen (现有列表内容)
     └─ PicksScreen (占位)
 └─ Settings → SettingsScreen (全屏)
 └─ RepoDetail → ReadmeScreen (全屏)
```

### Tab 切换策略：销毁重建（`when`）

使用 `when(selectedTab)` 条件渲染，切走的 tab 从组合树移除。

- ViewModel 数据（仓库列表、筛选条件等）不受影响，ViewModel 作用域在 HomeScreen 层级
- 丢失的仅是 LazyColumn 滚动位置，列表不长（~25 条），可接受
- 代码简单直接
- 未来如需保持滚动位置，可升级为 Box + 显隐方案，改动很小

## 文件结构变更

```
shared/src/commonMain/kotlin/whl/trending/ai/
├── core/
│   └── App.kt                        # 修改：Main → Home 路由
├── ui/
│   ├── home/
│   │   └── HomeScreen.kt             # 新增：Tab 容器
│   ├── trending/                      # 原 main/ 重命名
│   │   ├── TrendingScreen.kt         # 原 MainScreen，移除自身 Scaffold
│   │   └── TrendingViewModel.kt      # 原 MainViewModel
│   ├── picks/
│   │   └── PicksScreen.kt            # 新增：占位页面
│   ├── settings/                      # 不变
│   └── detail/                        # 不变
```

## 路由定义

```kotlin
// App.kt
data object Home          // 原 Main，指向 HomeScreen
data object Settings      // 不变
data class RepoDetail(val owner: String, val repo: String)  // 不变
```

## HomeScreen 设计

### Tab 枚举

```kotlin
enum class HomeTab { Trending, Picks }
```

### 状态管理

```kotlin
var selectedTab by rememberSaveable { mutableStateOf(HomeTab.Trending) }
```

### 结构

```kotlin
Scaffold(
    topBar = {
        // 根据 selectedTab 动态显示：
        // Trending: 原有标题 + 历史按钮 + 设置按钮
        // Picks: "精选" 标题 + 设置按钮
    },
    bottomBar = {
        NavigationBar {
            NavigationBarItem(
                selected = selectedTab == HomeTab.Trending,
                icon = Icons.Default.TrendingUp,
                label = "Trending"
            )
            NavigationBarItem(
                selected = selectedTab == HomeTab.Picks,
                icon = Icons.Default.Star,
                label = "精选"
            )
        }
    }
) { innerPadding ->
    when (selectedTab) {
        HomeTab.Trending -> TrendingScreen(onNavigateToDetail, onNavigateToSettings)
        HomeTab.Picks -> PicksScreen()
    }
}
```

### 导航回调

```kotlin
HomeScreen(
    onNavigateToSettings: () -> Unit,   // → backStack.add(Settings)
    onNavigateToDetail: (String, String) -> Unit  // → backStack.add(RepoDetail)
)
```

## NavigationBar 图标

| Tab | 图标 | Label |
|-----|------|-------|
| Trending | `Icons.Default.TrendingUp` | Trending |
| 精选 | `Icons.Default.Star` | 精选 |

## TrendingScreen 改造

- 包名从 `ui.main` 改为 `ui.trending`
- 类名从 `MainScreen` / `MainViewModel` 改为 `TrendingScreen` / `TrendingViewModel`
- 移除自身的 `Scaffold` 和 `TopAppBar`，只保留内容部分（列表 + BottomSheet 等）
- TopAppBar 相关逻辑（标题、历史按钮、设置按钮）上移到 HomeScreen

## PicksScreen 占位

居中显示 Star 图标 + "即将推出" 文字。

## Settings 入口

保持现状，从 TopAppBar 齿轮按钮进入，不放入底部栏。
