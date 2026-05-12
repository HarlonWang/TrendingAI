# 用户自选品牌色 · 设计文档

| 项 | 值 |
|----|----|
| 创建日期 | 2026-05-12 |
| 状态 | **草案** |
| 涉及仓库 | `TrendingAI`（客户端，KMP + Compose Multiplatform） |
| 触发动因 | 让用户能自选品牌色，提供运行时动态换肤体验 |
| 关键依赖 | [material-kolor](https://github.com/jordond/MaterialKolor)（Google `material-color-utilities` 的 KMP 移植，事实标准） |

---

## 1. 背景与目标

### 1.1 现状

- `shared/.../core/App.kt` 直接使用 `lightColorScheme()` / `darkColorScheme()` 默认 Material 3 baseline 配色
- `SettingsManager` 已支持 `themeMode`（跟随系统 / 浅色 / 深色），使用 multiplatform-settings 持久化
- iOS 侧 UI 全在 `shared`，主题逻辑可 100% 跨平台
- App icon 背景色为 `#6750A4`（M3 baseline 紫），与默认配色一致，将作为默认 seed
- HN / PH 品牌色 `#FF6600` / `#DA552F` 写死在屏幕代码中，**不参与换肤**

### 1.2 目标

- 用户能在「设置」中从 10 个预设色块挑选品牌色
- 切换瞬间整个 App 颜色平滑过渡（不是突变）
- 选择持久化，跨进程保留
- 跨 Android / iOS 行为一致
- 暗/浅色切换与品牌色选择互不影响

### 1.3 非目标（YAGNI 显式声明）

- ❌ 暗/亮模式独立 seed
- ❌ `contrastLevel` 开关
- ❌ Scheme variant 切换（固定 `TonalSpot`）
- ❌ Android 12+ Material You 系统壁纸色
- ❌ 自定义颜色取色器 / HSV / Hex 输入
- ❌ 实时预览弹窗 / 重置按钮（默认值即第一项，点 default 等同重置）
- ❌ 颜色历史记录
- ❌ 从内容（OG 图等）动态取色

---

## 2. 关键决策

| 决策 | 选择 | 备选 | 原因 |
|------|------|------|------|
| 色彩来源 | 预设调色板 | 任意取色 / 跟随壁纸 / 内容取色 | 简单可控，视觉一致 |
| Scheme variant | 固定 `TonalSpot` | 暴露给用户 | 资讯类 App 适合克制平衡风格，UX 最简 |
| 跟随系统壁纸色 | 不支持 | Android 12+ 暴露选项 | 避免 expect/actual 分支，跨平台一致 |
| 对比度开关 | 不暴露 | 提供 0/0.5/1.0 三档 | 暂无可访问性诉求，留待二期 |
| 默认 seed | `#6750A4` | 任选 | 与现有 app icon 背景色一致 |
| 过渡动画 | 600ms `FastOutSlowInEasing` | 无动画 / 自定义曲线 | material-kolor 默认参数，体验流畅且不打扰 |

---

## 3. 架构与文件布局

### 3.1 新增文件

```
shared/src/commonMain/kotlin/whl/trending/ai/ui/theme/
├── ThemePalette.kt        # 预设 seed 列表
├── TrendingTheme.kt       # @Composable TrendingTheme(content) — 唯一对外入口
└── rememberThemeState.kt  # 包装 material-kolor 的 rememberDynamicMaterialThemeState
```

### 3.2 改动文件

- `core/App.kt`：把直接调用 `MaterialTheme(...)` 替换为 `TrendingTheme { ... }`
- `data/local/SettingsManager.kt`：新增 `seedColor: Flow<Long>` + `setSeedColor(argb: Long)`
- `ui/settings/SettingsScreen.kt`：在「主题」分组下新增「主题色」分组（色块网格）
- `gradle/libs.versions.toml`：新增 `material-kolor` 版本与 alias（**禁止在 build.gradle.kts 硬编码坐标字符串**，遵守项目依赖管理规范）
- `shared/build.gradle.kts`：`commonMain.dependencies` 引用 catalog alias
- `commonMain/composeResources/values/strings.xml`（默认/英文） + `values-zh/strings.xml`（中文）：新增 10 个色名 + 「主题色」分组标题

### 3.3 现有屏幕的兼容性

所有屏幕已经在用 `MaterialTheme.colorScheme.*` / `MaterialTheme.typography.*`，`DynamicMaterialTheme` 内部仍把 `colorScheme` 注入到同一个 `LocalColorScheme`，**屏幕代码零改动**。

例外检查：HN / PH 品牌色（`Color(0xFFFF6600)` / `Color(0xFFDA552F)`）必须保持写死，不被误改成 `colorScheme.primary`。

---

## 4. 数据流

```
SettingsManager (multiplatform-settings · SharedPreferences / NSUserDefaults)
   ├─ themeMode: Flow<ThemeMode>        [既有]
   └─ seedColor: Flow<Long>             [新增, 存 ARGB Long]
                │
                ▼
        TrendingTheme (Composable, commonMain)
                │
                ├─ collectAsState(seedColor)
                ├─ collectAsState(themeMode) → isDark = resolve(isSystemInDarkTheme())
                │
                ▼
        rememberDynamicMaterialThemeState(
            seedColor      = Color(seedArgb.toULong()),
            isDark         = isDark,
            style          = PaletteStyle.TonalSpot,
            animationSpec  = tween(600, easing = FastOutSlowInEasing),
        )
                │
                ▼
        DynamicMaterialTheme(state) { content() }
```

- `seedColor` 变化 → material-kolor 内部 ARGB→HCT 路径插值 → 整个 `colorScheme` 平滑过渡
- `themeMode` 变化 → `isDark` 翻转 → 同样走过渡动画
- `MainActivity` 中 Android 状态栏 / 导航栏边到边样式继续随 `isDark` 更新（既有逻辑保留）

---

## 5. 模块详细设计

### 5.1 `ThemePalette.kt`

```kotlin
package whl.trending.ai.ui.theme

import org.jetbrains.compose.resources.StringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.*

data class ThemeSeed(
    val id: String,
    val nameRes: StringResource,
    val argb: Long,
)

val PRESET_PALETTE: List<ThemeSeed> = listOf(
    ThemeSeed("default", Res.string.theme_default, 0xFF6750A4),
    ThemeSeed("crimson", Res.string.theme_crimson, 0xFFDC362E),
    ThemeSeed("orange",  Res.string.theme_orange,  0xFFF4511E),
    ThemeSeed("amber",   Res.string.theme_amber,   0xFFFFB300),
    ThemeSeed("green",   Res.string.theme_green,   0xFF2E7D32),
    ThemeSeed("teal",    Res.string.theme_teal,    0xFF00897B),
    ThemeSeed("cyan",    Res.string.theme_cyan,    0xFF0288D1),
    ThemeSeed("blue",    Res.string.theme_blue,    0xFF1976D2),
    ThemeSeed("indigo",  Res.string.theme_indigo,  0xFF3F51B5),
    ThemeSeed("pink",    Res.string.theme_pink,    0xFFC2185B),
)

const val DEFAULT_SEED_ARGB: Long = 0xFF6750A4L
```

**选色逻辑**：HCT 色相均匀打散 0°–340°；亮度都在中段，避免过暗/过亮 seed 让 TonalSpot 生成的 primary 失真。

### 5.2 `rememberThemeState.kt`

```kotlin
@Composable
internal fun rememberTrendingThemeState(
    seedArgb: Long,
    isDark: Boolean,
): DynamicMaterialThemeState =
    rememberDynamicMaterialThemeState(
        seedColor      = Color(seedArgb.toULong()),
        isDark         = isDark,
        style          = PaletteStyle.TonalSpot,
        animationSpec  = tween(durationMillis = 600, easing = FastOutSlowInEasing),
    )
```

### 5.3 `TrendingTheme.kt`

```kotlin
@Composable
fun TrendingTheme(content: @Composable () -> Unit) {
    val themeMode by globalSettingsManager.themeMode
        .collectAsState(ThemeMode.FOLLOW_SYSTEM)
    val seedArgb by globalSettingsManager.seedColor
        .collectAsState(DEFAULT_SEED_ARGB)

    val isDark = when (themeMode) {
        ThemeMode.FOLLOW_SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT         -> false
        ThemeMode.DARK          -> true
    }

    val state = rememberTrendingThemeState(seedArgb, isDark)
    DynamicMaterialTheme(state = state, animate = true) {
        content()
    }
}
```

### 5.4 `SettingsManager` 扩展

```kotlin
private val SEED_COLOR_KEY = "prefs_seed_color"

val seedColor: Flow<Long> =
    settings.getLongFlow(SEED_COLOR_KEY, DEFAULT_SEED_ARGB)

fun setSeedColor(argb: Long) {
    settings.putLong(SEED_COLOR_KEY, argb)
}
```

### 5.5 `core/App.kt` 改造

把当前的

```kotlin
MaterialTheme(colorScheme = if (isDark) darkColorScheme() else lightColorScheme()) {
    NavDisplay(...)
}
```

替换为

```kotlin
TrendingTheme {
    NavDisplay(...)
}
```

`themeMode` 的 collect 与 `isDark` 计算移入 `TrendingTheme` 内部，`App.kt` 不再需要这块逻辑。

### 5.6 `SettingsScreen.kt` UI

在现有「主题」分组下新增分组：

```
┌────────────────────────────────────┐
│  主题                              │
│   ○ 跟随系统                       │
│   ● 浅色                           │
│   ○ 深色                           │
│                                    │
│  主题色                            │
│   [●][○][○][○][○]                │
│   [○][○][○][○][○]                │
└────────────────────────────────────┘
```

- 容器：2 行 5 列，`FlowRow` 自动换行 + `horizontalArrangement = Arrangement.spacedBy(12.dp)`
- 每个 swatch：圆形 `Surface`，直径 40dp，填该 seed 色
- 选中态：2dp `MaterialTheme.colorScheme.outline` 描边 + 中心 `Icons.Filled.Check` 图标，图标颜色按 seed 亮度自适应（亮度 < 0.5 用 white，否则 black）
- 点击：`globalSettingsManager.setSeedColor(seed.argb)`，无需手动 dismiss，主题动画过渡
- 触摸反馈：默认 `Modifier.clickable` 的 ripple
- a11y：`Modifier.semantics { contentDescription = stringResource(seed.nameRes); selected = (current == seed.argb) }`
- 选中态判定：`palette.firstOrNull { it.argb == current } ?: palette.first()` 兜底，UI 永远有选中项

### 5.7 依赖管理

`gradle/libs.versions.toml`：

```toml
[versions]
material-kolor = "x.y.z"   # 实施时取最新稳定版

[libraries]
material-kolor = { module = "com.materialkolor:material-kolor", version.ref = "material-kolor" }
```

`shared/build.gradle.kts` 在 `commonMain.dependencies` 中引用 `libs.material.kolor`。

---

## 6. 边界与错误处理

| 场景 | 行为 |
|------|------|
| 首次安装 | `getLongFlow` 默认值 `DEFAULT_SEED_ARGB`，UI 默认选中第一个色块 |
| 卸载重装 | 值丢失，回 default（multiplatform-settings 标准行为） |
| 存的值不在调色板内 | 理论上不可能（仅色块网格写入）。UI 渲染选中态时 `firstOrNull { it.argb == current } ?: palette.first()` 兜底 |
| 切换深浅色 | `seedColor` 不变，仅 `isDark` 翻转，材色重新生成，动画过渡 |
| material-kolor 在 iosArm64 / iosX64 构建失败 | 阻塞性问题；通过冒烟构建在 CI 早发现 |
| 选中 swatch 时设置写入失败 | multiplatform-settings 写 SharedPreferences / NSUserDefaults 是同步操作，失败极少；不做特殊处理，下次重试即可 |

---

## 7. 测试

### 7.1 单元测试（`shared/src/commonTest`）

- `SettingsManagerTest`（既有，新增 case）
  - `seedColor` 默认值为 `DEFAULT_SEED_ARGB`
  - `setSeedColor(x)` 后 `seedColor` Flow emit `x`
  - 修改 `seedColor` 不影响 `themeMode`，反之亦然
- `ThemePaletteTest`（新增）
  - `PRESET_PALETTE.size == 10`
  - 所有 `id` 唯一
  - 所有 `argb` 高字节 `== 0xFF`（无意外透明）
  - `DEFAULT_SEED_ARGB == PRESET_PALETTE.first().argb`

### 7.2 手动验收清单

1. Android：设置 → 主题色 → 点 10 个色块，每次 0.6s 平滑过渡，无闪烁
2. 切换深/浅色 → seed 不变，ColorScheme 跟着翻面
3. 杀进程重进 → seed 持久化
4. iOS：相同 4 步在 Xcode iOS 模拟器上跑一遍
5. 关键屏幕（Home、Picks、ReadmeScreen、Settings 自身、FavoriteList、Feedback、WebView）切色后视觉正常
6. HN（橙）/ PH（红）品牌色未被换肤（仍是 `#FF6600` / `#DA552F`）

---

## 8. 提交计划

实施阶段拆分到独立的 implementation plan，本设计预期分 3 个 commit：

1. `chore: 引入 material-kolor 依赖（version catalog）`
2. `feat: 新增 shared/ui/theme 模块，TrendingTheme 接管主题`
3. `feat: 设置页新增主题色选择，支持运行时动态换肤`

提交按项目规范使用中文 commit message，统一在 `feat/dynamic-theme-color` 分支上。
