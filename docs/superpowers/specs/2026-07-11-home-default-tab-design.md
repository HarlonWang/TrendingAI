# 首页默认 tab 设置 — 设计文档

日期：2026-07-11
分支：feat/home-default-tab

## 背景与目标

首页底部有 4 个 tab（GitHub / Hacker News / Product Hunt / Picks），当前冷启动默认选中写死为 `HomeTab.GitHub`（`shared/src/commonMain/kotlin/whl/trending/ai/ui/home/HomeScreen.kt:108`）。目标：允许用户在设置页选择冷启动时默认显示哪个 tab。

方案已确认：**设置项固定默认 tab**（不做「记住上次浏览」）。

## 数据层（SettingsManager.kt）

`shared/src/commonMain/kotlin/whl/trending/ai/data/local/SettingsManager.kt`：

- 新增 key 常量 `DEFAULT_HOME_TAB_KEY = "prefs_default_home_tab"`（沿用现有 `prefs_` 前缀约定）。
- 按现有偏好三件套模式暴露：
  - `val defaultHomeTab: Flow<String>` — `settings.getStringFlow(DEFAULT_HOME_TAB_KEY, "GitHub")`
  - `fun currentDefaultHomeTab(): String` — 同步读，默认 `"GitHub"`
  - `fun setDefaultHomeTab(name: String)` — 写入
- 存 `HomeTab.name` 字符串而非 ordinal：`HomeTab` 枚举定义在 ui 层，data 层不反向依赖 ui；字符串也抗枚举顺序调整。

## 首页（HomeScreen.kt）

- `selectedTabName` 的初始值从写死的 `HomeTab.GitHub.name` 改为读 `globalSettingsManager.currentDefaultHomeTab()`。
- 解析用 `runCatching { HomeTab.valueOf(it) }`，失败回落 `HomeTab.GitHub`（防御存储值异常/枚举改名）。该解析逻辑抽为可测的纯函数（如 `HomeTab.fromNameOrDefault(name)`）。
- 仅影响**初始值**：会话内切 tab、`rememberSaveable` 进程恢复、通知深链 `HomeTabRequest` 的消费时机与优先级均不变（深链在组合后消费，天然覆盖默认值）。

## 设置页（SettingsScreen.kt）

- 新增「默认首页」条目，参照现有「应用语言」的 `ListItem` + `DropdownMenu` 单选写法（4 个选项用 SegmentedButton 过挤）。
- 下拉选项：GitHub / Hacker News / Product Hunt / Picks——用全称，不用底部栏的 HN/PH 缩写；Picks 复用 `picks_title`。
- 条目放在主题、语言所在的通用分组。
- 选中即调 `globalSettingsManager.setDefaultHomeTab(tab.name)`，写入前按现有模式埋点：
  `trackEvent("settings_default_home_tab_change", mapOf("tab" to tab.name.lowercase()))`。

## 字符串资源

`shared/src/commonMain/composeResources/values/strings.xml` 与 `values-zh/strings.xml` 各新增：

- 设置项标题：Default home tab / 默认首页
- 选项显示名（GitHub、Hacker News、Product Hunt 如无现成全称字符串则新增；Picks 复用 `picks_title`）

## 测试

- `commonTest`：用内存版 `Settings`（`MapSettings`）覆盖 `SettingsManager` 默认值与读写。
- `HomeTab.fromNameOrDefault` 纯函数：合法值、非法值回落、空值回落。

## 埋点

沿用统一模式：`trackEvent("settings_default_home_tab_change", mapOf("tab" to <枚举>.name.lowercase()))`，置于设置页点击回调、写入之前。

## 不做（YAGNI）

- 不加「记住上次浏览的 tab」选项。
- iOS 无需特殊处理，全部改动在 commonMain。
- 不改底部导航 UI 与 tab 顺序。
