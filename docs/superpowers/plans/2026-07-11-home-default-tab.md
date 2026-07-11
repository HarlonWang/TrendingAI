# 首页默认 tab 设置 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 允许用户在设置页选择冷启动时首页默认显示哪个底部 tab（GitHub / Hacker News / Product Hunt / Picks），当前写死为 GitHub。

**Architecture:** KMP 分层架构，全部改动在 `shared/commonMain`。数据层 `SettingsManager` 新增字符串偏好 `default_home_tab`（存 `HomeTab.name`，默认 `"GitHub"`）；`HomeTab` 枚举增加防御性解析 `fromNameOrDefault`；`HomeScreen` 初始 tab 改为读该偏好；`SettingsScreen` 新增 ListItem + DropdownMenu 单选项，写入前埋点。

**Tech Stack:** Kotlin Multiplatform / Compose Multiplatform / multiplatform-settings（`com.russhwolf.settings`，测试用 `MapSettings`）/ kotlin.test + kotlinx-coroutines-test

**Spec:** `docs/superpowers/specs/2026-07-11-home-default-tab-design.md`

## Global Constraints

- 全部改动在 `shared/src/commonMain`（+ `commonTest`），不动 androidApp / iosApp。
- 偏好存 `HomeTab.name` 字符串而非 ordinal（data 层不得 import ui 层的 `HomeTab`；`SettingsManager` 只处理 String）。
- 埋点模式固定：`trackEvent("settings_default_home_tab_change", mapOf("tab" to tab.name.lowercase()))`，置于写入前（参照 SettingsScreen.kt:468 语言设置写法）。
- 字符串资源必须 en/zh 两份同步加（`values/strings.xml` 与 `values-zh/strings.xml` key 一一对应）。
- 单元测试命令：`./gradlew :shared:testAndroidHostTest`，全绿才允许 commit。
- Commit 规范：`feat:` 前缀，中文描述，结尾加 `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`。

---

### Task 1: SettingsManager 新增 defaultHomeTab 偏好

**Files:**
- Modify: `shared/src/commonMain/kotlin/whl/trending/ai/data/local/SettingsManager.kt`（key 常量区 :56 后、类尾 :287 前）
- Test: `shared/src/commonTest/kotlin/whl/trending/ai/data/local/SettingsManagerTest.kt`

**Interfaces:**
- Consumes: 现有 `SettingsManager(settings: ObservableSettings)` 与 `settings.getStringFlow/getString/putString`。
- Produces: `val defaultHomeTab: Flow<String>`、`fun currentDefaultHomeTab(): String`、`fun setDefaultHomeTab(name: String)`——Task 2/3 依赖这三个签名；默认值字符串为 `"GitHub"`。

- [ ] **Step 1: 写失败测试**

在 `SettingsManagerTest.kt` 末尾（`openLinksInCustomTab_defaults_true_and_persists` 之后、类结束花括号前）追加：

```kotlin
    @Test
    fun defaultHomeTab_defaults_to_github_and_persists() = runTest {
        // 默认值：GitHub
        assertEquals("GitHub", manager.currentDefaultHomeTab())
        assertEquals("GitHub", manager.defaultHomeTab.first())

        // 改为 Picks
        manager.setDefaultHomeTab("Picks")
        assertEquals("Picks", manager.currentDefaultHomeTab())
        assertEquals("Picks", manager.defaultHomeTab.first())

        // 模拟应用重启：同一份底层存储，新建 manager
        val rebuilt = SettingsManager(settings)
        assertEquals("Picks", rebuilt.currentDefaultHomeTab())
    }
```

（所需 import：`runTest`、`first`、`assertEquals` 均已存在于该文件。）

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew :shared:testAndroidHostTest --tests "whl.trending.ai.data.local.SettingsManagerTest" 2>&1 | tail -20`
Expected: 编译失败，报 `unresolved reference: currentDefaultHomeTab`（新 API 尚不存在）。

- [ ] **Step 3: 最小实现**

`SettingsManager.kt` 两处修改。

(a) key 常量区，在 `PICKS_NEWSLETTER_BANNER_DISMISSED_KEY`（:56）之后加：

```kotlin
    private val DEFAULT_HOME_TAB_KEY = "prefs_default_home_tab"
```

(b) 类内 `picksNewsletterBannerDismissed` 三件套（:287 附近）之后、类结束花括号前加：

```kotlin
    /**
     * 冷启动默认显示的首页 tab，存 ui 层 HomeTab 枚举的 name（如 "GitHub"、"Picks"）。
     * data 层不依赖 ui 层枚举，只存取字符串；解析与回落由 HomeTab.fromNameOrDefault 负责。
     * 只决定初始值；会话内切 tab 不回写此设置。
     */
    val defaultHomeTab: Flow<String> = settings.getStringFlow(DEFAULT_HOME_TAB_KEY, "GitHub")

    fun currentDefaultHomeTab(): String = settings.getString(DEFAULT_HOME_TAB_KEY, "GitHub")

    fun setDefaultHomeTab(name: String) {
        settings.putString(DEFAULT_HOME_TAB_KEY, name)
    }
```

（`getStringFlow` import 已存在，:9。）

- [ ] **Step 4: 运行测试确认通过**

Run: `./gradlew :shared:testAndroidHostTest --tests "whl.trending.ai.data.local.SettingsManagerTest" 2>&1 | tail -20`
Expected: `BUILD SUCCESSFUL`，全部用例 PASS。

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/whl/trending/ai/data/local/SettingsManager.kt shared/src/commonTest/kotlin/whl/trending/ai/data/local/SettingsManagerTest.kt
git commit -m "feat(settings): SettingsManager 新增首页默认 tab 偏好

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 2: HomeTab.fromNameOrDefault + HomeScreen 初始 tab 读设置

**Files:**
- Modify: `shared/src/commonMain/kotlin/whl/trending/ai/ui/home/HomeScreen.kt`（枚举 :94-96、初始值 :108-109）
- Create: `shared/src/commonTest/kotlin/whl/trending/ai/ui/home/HomeTabTest.kt`

**Interfaces:**
- Consumes: Task 1 的 `globalSettingsManager.currentDefaultHomeTab(): String`（`globalSettingsManager` 已在 HomeScreen.kt:81 import）。
- Produces: `HomeTab.fromNameOrDefault(name: String): HomeTab`（companion 函数，非法/未知输入回落 `HomeTab.GitHub`）——Task 3 的设置页也会用到。

- [ ] **Step 1: 写失败测试**

新建 `shared/src/commonTest/kotlin/whl/trending/ai/ui/home/HomeTabTest.kt`：

```kotlin
package whl.trending.ai.ui.home

import kotlin.test.Test
import kotlin.test.assertEquals

class HomeTabTest {

    @Test
    fun fromNameOrDefault_resolves_every_valid_name() {
        HomeTab.entries.forEach { tab ->
            assertEquals(tab, HomeTab.fromNameOrDefault(tab.name))
        }
    }

    @Test
    fun fromNameOrDefault_falls_back_to_github_on_unknown_name() {
        assertEquals(HomeTab.GitHub, HomeTab.fromNameOrDefault("NoSuchTab"))
    }

    @Test
    fun fromNameOrDefault_falls_back_to_github_on_blank() {
        assertEquals(HomeTab.GitHub, HomeTab.fromNameOrDefault(""))
    }

    @Test
    fun fromNameOrDefault_is_case_sensitive_like_storage() {
        // 存储值就是 HomeTab.name 原文，大小写不符视为非法、回落 GitHub
        assertEquals(HomeTab.GitHub, HomeTab.fromNameOrDefault("picks"))
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew :shared:testAndroidHostTest --tests "whl.trending.ai.ui.home.HomeTabTest" 2>&1 | tail -20`
Expected: 编译失败，报 `unresolved reference: fromNameOrDefault`。

- [ ] **Step 3: 最小实现**

`HomeScreen.kt` 两处修改。

(a) 枚举（:94-96）改为：

```kotlin
enum class HomeTab {
    GitHub, HackerNews, ProductHunt, Picks;

    companion object {
        /** 解析持久化的 tab name；非法值（枚举改名、脏数据）回落 GitHub */
        fun fromNameOrDefault(name: String): HomeTab =
            entries.firstOrNull { it.name == name } ?: GitHub
    }
}
```

(b) 初始值（:108-109）由：

```kotlin
    var selectedTabName by rememberSaveable { mutableStateOf(HomeTab.GitHub.name) }
    val selectedTab = HomeTab.valueOf(selectedTabName)
```

改为：

```kotlin
    // 冷启动进入设置页选的默认 tab；仅初始值，会话内切换与 rememberSaveable 恢复不受影响
    var selectedTabName by rememberSaveable {
        mutableStateOf(HomeTab.fromNameOrDefault(globalSettingsManager.currentDefaultHomeTab()).name)
    }
    val selectedTab = HomeTab.fromNameOrDefault(selectedTabName)
```

（`valueOf` 一并换成防御性解析；`globalSettingsManager` import 已存在 :81。）

- [ ] **Step 4: 运行测试确认通过**

Run: `./gradlew :shared:testAndroidHostTest --tests "whl.trending.ai.ui.home.HomeTabTest" 2>&1 | tail -20`
Expected: `BUILD SUCCESSFUL`，4 个用例 PASS。

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/whl/trending/ai/ui/home/HomeScreen.kt shared/src/commonTest/kotlin/whl/trending/ai/ui/home/HomeTabTest.kt
git commit -m "feat(home): 首页初始 tab 改为读默认 tab 设置，解析失败回落 GitHub

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 3: 设置页新增「默认首页」下拉单选 + 字符串资源

**Files:**
- Modify: `shared/src/commonMain/composeResources/values/strings.xml`（:53 `new_only_default_desc` 之后）
- Modify: `shared/src/commonMain/composeResources/values-zh/strings.xml`（:53 同位置）
- Modify: `shared/src/commonMain/kotlin/whl/trending/ai/ui/settings/SettingsScreen.kt`（状态收集 :180 附近、「只看 New」item :514-529 之后、文件尾私有函数区）

**Interfaces:**
- Consumes: Task 1 的 `defaultHomeTab: Flow<String>` / `currentDefaultHomeTab()` / `setDefaultHomeTab(name)`；Task 2 的 `HomeTab.fromNameOrDefault(name)`；现有字符串 `hackernews_title` / `producthunt_title` / `picks_title`。
- Produces: 设置页 UI 项（无下游代码依赖）；埋点事件 `settings_default_home_tab_change`。

- [ ] **Step 1: 新增字符串资源（en/zh 同步）**

`values/strings.xml` :53 `new_only_default_desc` 行后插入：

```xml
    <string name="default_home_tab">Default home tab</string>
    <string name="default_home_tab_desc">Which tab the app opens to at launch</string>
```

`values-zh/strings.xml` :53 同位置插入：

```xml
    <string name="default_home_tab">默认首页</string>
    <string name="default_home_tab_desc">启动时进入的标签页</string>
```

- [ ] **Step 2: SettingsScreen 状态收集与 import**

(a) import 区补齐（对照文件头现有风格，各自插到对应字母序附近即可）：

```kotlin
import androidx.compose.material.icons.filled.Home
import whl.trending.ai.ui.home.HomeTab
import trendingai.shared.generated.resources.default_home_tab
import trendingai.shared.generated.resources.default_home_tab_desc
import trendingai.shared.generated.resources.hackernews_title
import trendingai.shared.generated.resources.producthunt_title
import trendingai.shared.generated.resources.picks_title
```

注意：`picks_title` 等三个 tab 名字符串若已被该文件 import 则跳过重复项（当前未 import，需新增）。

(b) 状态收集，在 `trendingNewOnlyDefault`（:180）之后加一行：

```kotlin
    val defaultHomeTab by globalSettingsManager.defaultHomeTab.collectAsState(
        remember { globalSettingsManager.currentDefaultHomeTab() }
    )
```

- [ ] **Step 3: 新增设置项 UI**

在「只看 New」默认开关的 `item { ... }`（:514-529）之后插入：

```kotlin
            // 默认首页 tab：只决定冷启动进入哪个 tab，会话内切换不回写
            item {
                var expanded by remember { mutableStateOf(false) }
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.default_home_tab)) },
                    supportingContent = { Text(stringResource(Res.string.default_home_tab_desc)) },
                    leadingContent = { Icon(Icons.Default.Home, null) },
                    trailingContent = {
                        Box {
                            Text(
                                text = homeTabOptionText(HomeTab.fromNameOrDefault(defaultHomeTab)),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { expanded = true }
                            )
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                HomeTab.entries.forEach { tab ->
                                    DropdownMenuItem(
                                        text = { Text(homeTabOptionText(tab)) },
                                        onClick = {
                                            expanded = false
                                            trackEvent("settings_default_home_tab_change", mapOf("tab" to tab.name.lowercase()))
                                            globalSettingsManager.setDefaultHomeTab(tab.name)
                                        }
                                    )
                                }
                            }
                        }
                    }
                )
            }
```

- [ ] **Step 4: 新增选项显示名函数**

在文件尾私有函数区（`languageOptionText` 附近）加：

```kotlin
@Composable
private fun homeTabOptionText(tab: HomeTab): String = when (tab) {
    HomeTab.GitHub -> "GitHub"
    HomeTab.HackerNews -> stringResource(Res.string.hackernews_title)
    HomeTab.ProductHunt -> stringResource(Res.string.producthunt_title)
    HomeTab.Picks -> stringResource(Res.string.picks_title)
}
```

（"GitHub" 为品牌名，中英一致，与首页底部栏的写法保持字面量；其余复用现有 title 字符串。）

- [ ] **Step 5: 编译 + 全量测试**

Run: `./gradlew :shared:testAndroidHostTest 2>&1 | tail -20`
Expected: `BUILD SUCCESSFUL`（资源生成 + 编译 + 既有/新增测试全绿）。

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/composeResources/values/strings.xml shared/src/commonMain/composeResources/values-zh/strings.xml shared/src/commonMain/kotlin/whl/trending/ai/ui/settings/SettingsScreen.kt
git commit -m "feat(settings): 设置页新增「默认首页」tab 选择项

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 4: 运行时验证（真机行为取证）

**Files:**
- 无代码改动；产出验证截图/结论。

**Interfaces:**
- Consumes: Task 1-3 的完整功能。
- Produces: 验证结论与截图证据。

- [ ] **Step 1: 按项目 verify 配方构建安装并驱动 UI**

使用项目的 `verify` skill（构建、安装到 Pixel_9_2 模拟器、驱动 UI、截图取证），验证以下场景：

1. 设置页出现「默认首页」条目，点开下拉可见 GitHub / Hacker News / Product Hunt / Picks 四项。
2. 选择 Picks 后，杀进程冷启动 app，首页落在 Picks tab。
3. 会话内手动切到 GitHub 再进设置页，「默认首页」仍显示 Picks（切 tab 不回写）。
4. 改回 GitHub，冷启动落在 GitHub tab。

Expected: 四个场景全部符合预期，留存关键截图。

- [ ] **Step 2: 汇报验证结果**

整理场景结论 + 截图路径，向用户汇报；不符合预期则回到对应 Task 修复。
