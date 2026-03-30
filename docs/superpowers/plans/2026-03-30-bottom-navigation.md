# Bottom Navigation 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 TrendingAI 添加 M3 NavigationBar 底部导航栏，包含 Trending 和精选两个 tab。

**Architecture:** 新增 HomeScreen 作为 tab 容器（Scaffold + NavigationBar），将现有 MainScreen 重命名为 TrendingScreen 并降级为纯内容组件（移除自身 Scaffold），新增 PicksScreen 占位。App.kt 路由从 Main 改为 Home。

**Tech Stack:** Kotlin, Compose Multiplatform 1.10.1, Material 3, Navigation3 UI 1.0.0-alpha06

---

## 文件结构

| 操作 | 文件路径 | 职责 |
|------|---------|------|
| 新建 | `shared/src/commonMain/kotlin/whl/trending/ai/ui/home/HomeScreen.kt` | Tab 容器，持有 Scaffold + NavigationBar + TopAppBar，通过 `when` 切换 tab 内容 |
| 新建 | `shared/src/commonMain/kotlin/whl/trending/ai/ui/picks/PicksScreen.kt` | 精选占位页 |
| 重命名+修改 | `ui/main/MainScreen.kt` → `ui/trending/TrendingScreen.kt` | 移除 Scaffold 和 TopAppBar，只保留列表内容 |
| 重命名+修改 | `ui/main/MainViewModel.kt` → `ui/trending/TrendingViewModel.kt` | 包名和类名重命名 |
| 修改 | `core/App.kt` | 路由 `Main` → `Home`，引用 HomeScreen |

所有路径前缀：`shared/src/commonMain/kotlin/whl/trending/ai/`

---

### Task 1: 重命名 MainScreen → TrendingScreen

将 `ui/main/` 包重命名为 `ui/trending/`，类名同步更新。

**Files:**
- Rename: `ui/main/MainScreen.kt` → `ui/trending/TrendingScreen.kt`
- Rename: `ui/main/MainViewModel.kt` → `ui/trending/TrendingViewModel.kt`
- Modify: `core/App.kt` — 更新 import 路径

- [ ] **Step 1: 创建 `ui/trending/TrendingViewModel.kt`**

从 `ui/main/MainViewModel.kt` 复制，修改包名和类名：

```kotlin
package whl.trending.ai.ui.trending

import whl.trending.ai.data.model.TrendingRepo
import whl.trending.ai.data.repository.TrendingRepository
import whl.trending.ai.core.DateTimeUtils
import whl.trending.ai.data.local.SettingsManager
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.core.platform.getSystemLanguage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TrendingUiState(
    val repos: List<TrendingRepo> = emptyList(),
    val since: String = "",
    val capturedAt: String = "",
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val selectedPeriod: String = "daily",
    val selectedLanguage: String = "all",
    val selectedDate: String? = null,
    val selectedBatch: String? = null,
    val error: String? = null
)

class TrendingViewModel(
    private val repository: TrendingRepository = TrendingRepository(),
    private val settingsManager: SettingsManager = globalSettingsManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(TrendingUiState())
    val uiState: StateFlow<TrendingUiState> = _uiState.asStateFlow()

    private var fetchJob: Job? = null

    init {
        fetchData()

        viewModelScope.launch {
            settingsManager.appLanguage.drop(1).collect {
                fetchData(isRefresh = true)
            }
        }
    }

    fun fetchData(isRefresh: Boolean = false) {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            if (isRefresh) {
                _uiState.update { it.copy(isRefreshing = true, error = null) }
                delay(500)
            } else {
                _uiState.update { it.copy(isLoading = true, error = null) }
            }

            try {
                val currentAppLanguage = settingsManager.appLanguage.first()
                val summaryLang = currentAppLanguage.isoCode ?: getSystemLanguage()

                val response = repository.getTrending(
                    _uiState.value.selectedPeriod,
                    _uiState.value.selectedLanguage,
                    summaryLang,
                    _uiState.value.selectedDate,
                    _uiState.value.selectedBatch
                )
                _uiState.update {
                    it.copy(
                        repos = response.data,
                        since = response.metadata.since,
                        capturedAt = DateTimeUtils.formatToLocalTime(response.metadata.capturedAt),
                        isLoading = false,
                        isRefreshing = false,
                        error = null
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = e.message ?: "Unknown Error"
                    )
                }
            }
        }
    }

    fun updateFilter(period: String, language: String) {
        if (_uiState.value.selectedPeriod == period &&
            _uiState.value.selectedLanguage == language) return

        _uiState.update {
            it.copy(
                selectedPeriod = period,
                selectedLanguage = language
            )
        }
        fetchData()
    }

    fun updateHistoryFilter(date: String?, batch: String?) {
        if (_uiState.value.selectedDate == date &&
            _uiState.value.selectedBatch == batch) return

        _uiState.update {
            it.copy(
                selectedDate = date,
                selectedBatch = batch
            )
        }
        fetchData()
    }
}
```

- [ ] **Step 2: 创建 `ui/trending/TrendingScreen.kt`**

从 `ui/main/MainScreen.kt` 复制，做以下修改：
1. 包名改为 `whl.trending.ai.ui.trending`
2. 函数名 `MainScreen` → `TrendingScreen`
3. 引用 `MainViewModel` → `TrendingViewModel`，`MainUiState` → `TrendingUiState`
4. **移除** `Scaffold` 和 `TrendingTopBar`（TopAppBar 将上移到 HomeScreen）
5. `TrendingScreen` 直接渲染列表内容，接收额外的 `modifier: Modifier` 参数

新的 `TrendingScreen` 签名和顶层结构：

```kotlin
package whl.trending.ai.ui.trending

// ... imports 保持不变，移除 Scaffold/TopAppBar/TopAppBarDefaults/TopAppBarScrollBehavior 相关 import
// 移除 nestedScroll import

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TrendingScreen(
    onNavigateToDetail: (owner: String, repo: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TrendingViewModel = viewModel { TrendingViewModel() }
) {
    val uiState by viewModel.uiState.collectAsState()
    var showFilterSheet by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }

    RepoList(
        uiState = uiState,
        modifier = modifier.fillMaxSize(),
        onRefresh = { viewModel.fetchData(isRefresh = true) },
        onNavigateToDetail = onNavigateToDetail
    )

    if (showFilterSheet) {
        FilterBottomSheet(
            selectedPeriod = uiState.selectedPeriod,
            selectedLanguage = uiState.selectedLanguage,
            onDismiss = { showFilterSheet = false },
            onConfirm = { period, language ->
                trackEvent("filter_confirm", mapOf("period" to period, "language" to language))
                viewModel.updateFilter(period, language)
                showFilterSheet = false
            }
        )
    }

    if (showHistorySheet) {
        HistoryBottomSheet(
            selectedDate = uiState.selectedDate,
            selectedBatch = uiState.selectedBatch,
            onDismiss = { showHistorySheet = false },
            onConfirm = { date, batch ->
                trackEvent("history_confirm", mapOf("date" to (date ?: ""), "batch" to (batch ?: "")))
                viewModel.updateHistoryFilter(date, batch)
                showHistorySheet = false
            }
        )
    }
}
```

注意：`showFilterSheet` 和 `showHistorySheet` 保留在 TrendingScreen 中，但触发入口将从 HomeScreen 的 TopAppBar 回调进来。为此，TrendingScreen 需要暴露这两个操作：

```kotlin
@Composable
fun TrendingScreen(
    onNavigateToDetail: (owner: String, repo: String) -> Unit,
    showFilterSheet: Boolean,
    onDismissFilterSheet: () -> Unit,
    showHistorySheet: Boolean,
    onDismissHistorySheet: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TrendingViewModel = viewModel { TrendingViewModel() }
)
```

不对——这会让 HomeScreen 管理 TrendingScreen 的 BottomSheet 状态，耦合太深。更好的方案：**TopAppBar 的按钮回调直接触发 TrendingScreen 内部状态**。做法是让 TrendingScreen 通过回调把触发函数暴露出去：

实际上最简方案：**TopAppBar 中的筛选和历史按钮的 onClick 通过 lambda 传入 TrendingScreen，TrendingScreen 内部控制 sheet 显隐。** 但 TopAppBar 在 HomeScreen 里，TrendingScreen 在 content 区域里，它们之间需要通信。

最终方案：用状态提升。HomeScreen 持有 `showFilterSheet` 和 `showHistorySheet` 状态，传给 TrendingScreen：

```kotlin
// HomeScreen 中
var showFilterSheet by rememberSaveable { mutableStateOf(false) }
var showHistorySheet by rememberSaveable { mutableStateOf(false) }

// TopAppBar onClick 设置这些状态
// TrendingScreen 接收这些状态并显示对应 BottomSheet
```

TrendingScreen 最终签名：

```kotlin
@Composable
fun TrendingScreen(
    onNavigateToDetail: (owner: String, repo: String) -> Unit,
    showFilterSheet: Boolean,
    onDismissFilterSheet: () -> Unit,
    showHistorySheet: Boolean,
    onDismissHistorySheet: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TrendingViewModel = viewModel { TrendingViewModel() }
)
```

保留其余所有 private Composable 函数（RepoList、RepoItem、ContributorAvatars、AvatarCircle、AiSummaryBox、RepoMetadata、FilterBottomSheet、HistoryBottomSheet、InfoDialog、BottomSheetHeader、toColorOrNull 扩展）不变，只是包名更新。

- [ ] **Step 3: 删除旧的 `ui/main/` 目录**

```bash
rm -rf shared/src/commonMain/kotlin/whl/trending/ai/ui/main/
```

- [ ] **Step 4: 更新 `core/App.kt` 的 import**

将：
```kotlin
import whl.trending.ai.ui.main.MainScreen
```
改为：
```kotlin
import whl.trending.ai.ui.trending.TrendingScreen
```

`MainScreen(...)` 调用暂时改为 `TrendingScreen(...)`，下一个 Task 会替换为 HomeScreen。

- [ ] **Step 5: 编译验证**

```bash
cd /Users/wanghl/TrendingProjects/TrendingAI && ./gradlew :shared:compileKotlinIosArm64 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
cd /Users/wanghl/TrendingProjects/TrendingAI
git add shared/src/commonMain/kotlin/whl/trending/ai/ui/trending/ \
        shared/src/commonMain/kotlin/whl/trending/ai/core/App.kt
git rm -r shared/src/commonMain/kotlin/whl/trending/ai/ui/main/
git commit -m "refactor: rename MainScreen to TrendingScreen, move to ui.trending package"
```

---

### Task 2: 创建 PicksScreen 占位页

**Files:**
- Create: `shared/src/commonMain/kotlin/whl/trending/ai/ui/picks/PicksScreen.kt`

- [ ] **Step 1: 创建 `PicksScreen.kt`**

```kotlin
package whl.trending.ai.ui.picks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PicksScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "即将推出",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
```

- [ ] **Step 2: Commit**

```bash
cd /Users/wanghl/TrendingProjects/TrendingAI
git add shared/src/commonMain/kotlin/whl/trending/ai/ui/picks/PicksScreen.kt
git commit -m "feat: add PicksScreen placeholder"
```

---

### Task 3: 创建 HomeScreen（Tab 容器）

**Files:**
- Create: `shared/src/commonMain/kotlin/whl/trending/ai/ui/home/HomeScreen.kt`

- [ ] **Step 1: 创建 `HomeScreen.kt`**

HomeScreen 持有 Scaffold（TopAppBar + NavigationBar），根据 selectedTab 切换内容和 TopAppBar。

```kotlin
package whl.trending.ai.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.GitHub_Invertocat_Black
import trendingai.shared.generated.resources.GitHub_Invertocat_White
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.app_name
import trendingai.shared.generated.resources.batch_am
import trendingai.shared.generated.resources.batch_pm
import trendingai.shared.generated.resources.history_trending
import trendingai.shared.generated.resources.period_daily
import trendingai.shared.generated.resources.period_monthly
import trendingai.shared.generated.resources.period_weekly
import trendingai.shared.generated.resources.settings
import whl.trending.ai.ui.picks.PicksScreen
import whl.trending.ai.ui.trending.TrendingScreen
import whl.trending.ai.ui.trending.TrendingViewModel

enum class HomeTab {
    Trending, Picks
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToDetail: (owner: String, repo: String) -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(HomeTab.Trending) }
    var showFilterSheet by rememberSaveable { mutableStateOf(false) }
    var showHistorySheet by rememberSaveable { mutableStateOf(false) }

    val trendingViewModel: TrendingViewModel = viewModel { TrendingViewModel() }
    val trendingUiState by trendingViewModel.uiState.collectAsState()

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            when (selectedTab) {
                HomeTab.Trending -> TrendingTopBar(
                    selectedPeriod = trendingUiState.selectedPeriod,
                    selectedLanguage = trendingUiState.selectedLanguage,
                    selectedDate = trendingUiState.selectedDate,
                    selectedBatch = trendingUiState.selectedBatch,
                    scrollBehavior = scrollBehavior,
                    onTitleClick = { showFilterSheet = true },
                    onHistoryClick = { showHistorySheet = true },
                    onNavigateToSettings = onNavigateToSettings
                )
                HomeTab.Picks -> PicksTopBar(
                    onNavigateToSettings = onNavigateToSettings
                )
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == HomeTab.Trending,
                    onClick = { selectedTab = HomeTab.Trending },
                    icon = { Icon(Icons.Default.TrendingUp, contentDescription = "Trending") },
                    label = { Text("Trending") }
                )
                NavigationBarItem(
                    selected = selectedTab == HomeTab.Picks,
                    onClick = { selectedTab = HomeTab.Picks },
                    icon = { Icon(Icons.Default.Star, contentDescription = "精选") },
                    label = { Text("精选") }
                )
            }
        }
    ) { innerPadding ->
        when (selectedTab) {
            HomeTab.Trending -> TrendingScreen(
                onNavigateToDetail = onNavigateToDetail,
                showFilterSheet = showFilterSheet,
                onDismissFilterSheet = { showFilterSheet = false },
                showHistorySheet = showHistorySheet,
                onDismissHistorySheet = { showHistorySheet = false },
                modifier = Modifier.padding(innerPadding),
                viewModel = trendingViewModel
            )
            HomeTab.Picks -> PicksScreen(
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrendingTopBar(
    selectedPeriod: String,
    selectedLanguage: String,
    selectedDate: String?,
    selectedBatch: String?,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior,
    onTitleClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val periodLabel = when (selectedPeriod) {
        "daily" -> stringResource(Res.string.period_daily)
        "weekly" -> stringResource(Res.string.period_weekly)
        "monthly" -> stringResource(Res.string.period_monthly)
        else -> selectedPeriod
    }

    TopAppBar(
        title = {
            Column(
                modifier = Modifier
                    .clickable { onTitleClick() }
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(Res.string.app_name),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp).padding(start = 4.dp)
                    )
                }

                val langLabel = selectedLanguage.replaceFirstChar { it.uppercase() }
                val subTitle = buildString {
                    append("$periodLabel · $langLabel")
                    if (!selectedDate.isNullOrEmpty()) {
                        val batchLabel = if (selectedBatch == "am") stringResource(Res.string.batch_am) else stringResource(Res.string.batch_pm)
                        append(" · $selectedDate ($batchLabel)")
                    }
                }

                Text(
                    text = subTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        scrollBehavior = scrollBehavior,
        navigationIcon = {
            IconButton(onClick = {}) {
                Icon(
                    painter = painterResource(
                        if (isDarkTheme) Res.drawable.GitHub_Invertocat_White
                        else Res.drawable.GitHub_Invertocat_Black
                    ),
                    contentDescription = "GitHub",
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        actions = {
            IconButton(onClick = onHistoryClick) {
                Icon(Icons.Default.DateRange, contentDescription = stringResource(Res.string.history_trending))
            }
            IconButton(onClick = onNavigateToSettings) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(Res.string.settings))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PicksTopBar(
    onNavigateToSettings: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text = "精选",
                style = MaterialTheme.typography.titleMedium
            )
        },
        actions = {
            IconButton(onClick = onNavigateToSettings) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(Res.string.settings))
            }
        }
    )
}
```

- [ ] **Step 2: Commit**

```bash
cd /Users/wanghl/TrendingProjects/TrendingAI
git add shared/src/commonMain/kotlin/whl/trending/ai/ui/home/HomeScreen.kt
git commit -m "feat: add HomeScreen with NavigationBar and tab switching"
```

---

### Task 4: 更新 App.kt 路由

**Files:**
- Modify: `shared/src/commonMain/kotlin/whl/trending/ai/core/App.kt`

- [ ] **Step 1: 更新 App.kt**

```kotlin
package whl.trending.ai.core

import whl.trending.ai.ui.detail.ReadmeScreen
import whl.trending.ai.ui.home.HomeScreen
import whl.trending.ai.ui.settings.SettingsScreen
import whl.trending.ai.data.local.ThemeMode
import whl.trending.ai.data.local.globalSettingsManager

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay

data object Home
data object Settings
data class RepoDetail(val owner: String, val repo: String)

@Composable
@Preview
fun App() {
    val backStack = remember { mutableStateListOf<Any>(Home) }
    val themeMode by globalSettingsManager.themeMode.collectAsState(ThemeMode.FOLLOW_SYSTEM)

    val isDark = when (themeMode) {
        ThemeMode.FOLLOW_SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    MaterialTheme(
        colorScheme = if (isDark) darkColorScheme() else lightColorScheme()
    ) {
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            entryProvider = { key ->
                when (key) {
                    is Home -> NavEntry(key) {
                        HomeScreen(
                            onNavigateToSettings = {
                                backStack.add(Settings)
                            },
                            onNavigateToDetail = { owner, repo ->
                                backStack.add(RepoDetail(owner, repo))
                            }
                        )
                    }

                    is Settings -> NavEntry(key) {
                        SettingsScreen(
                            onBack = {
                                backStack.removeLastOrNull()
                            }
                        )
                    }

                    is RepoDetail -> NavEntry(key) {
                        ReadmeScreen(
                            owner = key.owner,
                            repo = key.repo,
                            onBack = { backStack.removeLastOrNull() }
                        )
                    }

                    else -> {
                        error("Unknown route: $key")
                    }
                }
            }
        )
    }
}
```

- [ ] **Step 2: Commit**

```bash
cd /Users/wanghl/TrendingProjects/TrendingAI
git add shared/src/commonMain/kotlin/whl/trending/ai/core/App.kt
git commit -m "refactor: update App.kt routes from Main to Home"
```

---

### Task 5: 编译验证与手动测试

- [ ] **Step 1: 编译 shared 模块**

```bash
cd /Users/wanghl/TrendingProjects/TrendingAI && ./gradlew :shared:compileKotlinIosArm64 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 编译 Android 应用**

```bash
cd /Users/wanghl/TrendingProjects/TrendingAI && ./gradlew :androidApp:assembleDebug 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 手动验证清单**

在 Android 模拟器或真机上安装调试包，验证以下场景：

1. 底部栏显示两个 tab：Trending（TrendingUp 图标）和精选（Star 图标）
2. 默认选中 Trending tab，显示 GitHub Trending 列表
3. 点击精选 tab，底部栏高亮切换，内容区显示"即将推出"占位
4. 切回 Trending tab，列表正常显示（数据来自 ViewModel，不会重新请求 API）
5. Trending tab 的 TopAppBar 显示标题、筛选下拉、历史按钮、设置按钮
6. 精选 tab 的 TopAppBar 只显示"精选"标题和设置按钮
7. 点击设置按钮正常跳转设置页，返回后底部栏 tab 状态保持
8. 点击仓库进入详情页正常，返回后底部栏 tab 状态保持
9. 筛选和历史 BottomSheet 正常弹出和关闭

- [ ] **Step 4: 最终 Commit（如有修复）**

```bash
cd /Users/wanghl/TrendingProjects/TrendingAI
git add -A
git commit -m "fix: address issues found during manual testing"
```
