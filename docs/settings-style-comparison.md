# 设置页与主题实现对比：Echo / Rhythm / EchoFlow vs TrendingAI

三个参照仓库常驻 `TrendingProjects/.refs/` （见父目录 `CLAUDE.md` 的「参照仓库」节）。本文对比它们与 TrendingAI 在**主题层**和**设置页组件层**的实现，并给出对齐方案。2026-08-05 成文，代码位置以当时的 HEAD 为准。

---

## 一、一句话结论

**差异在组件范式，不在颜色和字号。**

三个参照仓库共享同一套「**连体卡片式设置组**」：每个设置项是一张独立卡片，同组内首尾大圆角、中间小圆角、彼此留 4dp 缝，图标装在 40dp 的着色容器里。Echo 和 Rhythm 甚至有**同名文件** `Material3SettingsGroup.kt`，连注释里的圆角规则都一致；EchoFlow 换了名字（`groupedItemShape()`），做的是同一件事。

TrendingAI 用的是**裸 `ListItem` + `HorizontalDivider`** 的标准 M3 列表范式：没有卡片、没有圆角、没有图标容器，靠分隔线分组。

所以你看到的"它们仨像、我们不像"，根源是这个。反过来说，只要补上这套组件，视觉就能对齐——主题层我们其实比它们做得更多（见第四节）。

---

## 二、设置项范式对比（核心）

| 维度 | Echo | Rhythm | EchoFlow | **TrendingAI** |
|---|---|---|---|---|
| 组件文件 | `ui/component/Material3SettingsGroup.kt` | `shared/presentation/components/Material3SettingsGroup.kt` | `ui/components/ExpressiveComponents.kt` + `ui/screens/SettingsComponents.kt` | 无，直接在 `SettingsScreen.kt` 里堆 `ListItem` |
| 单项容器 | `Card`，elevation 0 | `Card`，elevation 0 | `Card` / `Surface` | 无容器 |
| 容器底色 | `surfaceVariant` @30% | `surfaceContainer`（可传参） | `surface` / `surfaceContainerLow` | `surface`（ListItem 默认） |
| 圆角 | 单项 24；首 24/6，中 6，末 6/24 | 同左，另支持 `itemShape`/`lastItemShape` 覆盖以拼接更大卡组 | `groupedItemShape(large=22, small=6)` | 无圆角 |
| 项间距 | 4dp | 4dp | Spacing 体系 | 0（贴合） |
| 组间分隔 | 靠圆角+标题 | 靠圆角+标题 | 靠圆角+标题 | `HorizontalDivider` |
| 行内边距 | h20 / v16（compact h14/v10） | h21 / v16 | h16 / v12 | ListItem 默认 h16 / v8~12 |
| 图标容器 | 40dp，圆角 12dp 方形 | 40dp，**圆形** `Surface` | 40dp，**多边形** `RoundedPolygonShape(MaterialShapes.Cookie4Sided)` | **无容器**，裸 `Icon` |
| 容器底色 | `primary` @10%（高亮 15%） | `secondaryContainer`（高亮 `primaryContainer` + tonalElevation 2dp） | `secondaryContainer` / `tertiaryContainer` | — |
| 图标尺寸/色 | 24dp，`primary` @90% | 24dp，`onSecondaryContainer` | 20dp，`onSecondaryContainer` | 24dp，`onSurfaceVariant`（默认） |
| 图标↔文字间距 | 16dp（自定义图标 20dp） | 16dp | 16dp | ListItem 默认 |
| 标题 | `titleMedium` / `onSurface` | `titleMedium` / `onSurface` | `titleMedium` | `bodyLarge`（ListItem 默认） |
| 副标题 | `bodyMedium` / `onSurfaceVariant`，间隔 2dp | 同左 | `bodyMedium` | `bodyMedium`（ListItem 默认） |
| 分组标题 | `labelLarge` + `primary`，上下 8dp | `labelLarge` + `primary`，上 18 下 8 | `labelLarge` + `primary`，start16/top4/bottom8 | **`labelLarge` + `primary`，h16 v8** ✅ 已一致 |
| 按压反馈 | 默认 ripple | **缩放 0.97 + spring(MediumBouncy)**，关掉 ripple | 默认 | 默认 ripple |
| 数据结构 | `data class Material3SettingsItem`（11 字段） | 同名（12 字段，多 `scope`/`leadingContent`） | 每类行一个 composable | 无，逐个手写 |

**唯一已经对齐的一格**：分组标题样式（`SettingsScreen.kt:438` 的 `SettingsHeader`）——`labelLarge` + `primary` 与三家完全一致。其余全不同。

### 调用方式的差别

三家都是**声明式清单**，一组就是一个 `items = listOf(...)`：

```kotlin
// Rhythm: GoSettingsScreen.kt:178
Material3SettingsGroup(
    title = stringResource(R.string.streaming_settings_group_services),
    items = listOf(
        Material3SettingsItem(
            icon = MaterialSymbolIcon("cloud_queue"),
            title = { Text(...) },
            description = { Text(selectedService) },
            onClick = { showServiceSheet = true },
        ),
        ...
    )
)
```

我们是**逐项手写**（`SettingsScreen.kt:205` 起）：每项一个 `LazyColumn.item { ListItem(...) }`，圆角、间距、图标容器这些无处安放，加一项就要复制一遍模板。这也是 589 行的由来。

---

## 三、三家为什么长得像

把三份实现叠在一起看，共同点收敛成五条，可以直接当作"这套风格"的定义：

1. **连体卡片组** — 首项顶部大圆角、末项底部大圆角、中间一律 6dp，组内留 4dp 缝。视觉上是一整块被切开的圆角面板。大圆角取值：Echo/Rhythm 24dp，EchoFlow 22dp。
2. **图标必有容器** — 统一 40dp，填一个 container 系颜色，图标 20–24dp。形状是三家唯一的分歧点（圆角方 / 圆 / 多边形）。
3. **标题升一级** — `titleMedium` 而非 M3 `ListItem` 默认的 `bodyLarge`，副标题 `bodyMedium`，行高因此比标准 list item 高一截（约 72–80dp）。
4. **不用分隔线** — 分组靠卡片边界和 `primary` 色小标题，页面上没有任何 `HorizontalDivider`。
5. **数据驱动** — 一个 `SettingsItem` 数据类 + 一个渲染器，页面只声明内容。

---

## 四、主题层对比

| 维度 | Echo | Rhythm | EchoFlow | **TrendingAI** |
|---|---|---|---|---|
| 配色来源 | Material You 壁纸动态色，不可用时回落手写 scheme | 动态色 + **多套手写预设 palette** | 动态色 + 品牌 `darkColorScheme` | **materialKolor 由 seed 生成**（15 个预设 + 调色台自定义） |
| 壁纸动态色 | ✅ | ✅ | ✅ | ❌ 不支持 |
| 纯黑档 | `ColorScheme.pureBlack()` 覆盖 `surface`/`background` = `Color.Black` | `amoledTheme` 参数，深色时覆盖 | 无 | ✅ materialKolor 的 `isAmoled` |
| 对比度档位 | ❌ | ❌ | ❌ | ✅ `contrastLevel` |
| 配色风格档位 | ❌ | 主题预设切换 | ❌ | ✅ `style`（TonalSpot/Vibrant/…） |
| 色规版本 | 库默认 | 库默认 | 库默认 | ✅ 显式 `SPEC_2025` |
| 形状体系 | 无集中定义 | `Shape.kt` + `MaterialShapesUtils.kt` | `Shape.kt` + `ExpressiveShapes.kt` | 无集中定义 |
| 间距体系 | 散在各处 | 散在各处 | ✅ `Spacing.kt`（xs4/s8/m12/base16/l20/xl24/xxl32/huge48） | 散在各处 |
| 动效体系 | 散在各处 | 组件内 spring | ✅ `Motion.kt` | 散在各处 |

**结论**：主题层我们不落后，反而在可定制性上最强（seed 调色台 + 对比度 + 风格档 + SPEC_2025 是它们都没有的）。真正缺的是**壁纸动态色**（Material You）这一项，以及 EchoFlow 那种把间距/形状/动效抽成常量的工程习惯。

---

## 五、差异清单（按肉眼可感知度排序）

| # | 差异 | 感知度 | 对齐成本 |
|---|---|---|---|
| 1 | 卡片组 vs 裸列表 | ★★★★★ | 中 |
| 2 | 图标有无容器 | ★★★★☆ | 小（随 #1 一起做） |
| 3 | 标题字号 `bodyLarge` → `titleMedium` | ★★★☆☆ | 小 |
| 4 | 分隔线 vs 圆角分组 | ★★★☆☆ | 小（随 #1 消失） |
| 5 | 按压缩放反馈（仅 Rhythm 有） | ★★☆☆☆ | 小 |
| 6 | 页面主标题层级（`displaySmall`/`titleLarge`，见 `home-redesign-handover.md` 待办 A2） | ★★☆☆☆ | 小 |
| 7 | 间距常量体系 | ★☆☆☆☆（不可见，影响维护） | 中 |
| 8 | 壁纸动态色 | 不可见（能力缺失） | 中大，且与现有调色台冲突，**建议不做** |

---

## 六、对齐方案

### 6.1 新增组件

在 `shared/src/commonMain/kotlin/whl/trending/ai/ui/common/` 下新建 `SettingsGroup.kt`，提供：

```kotlin
data class SettingsItem(
    val icon: ImageVector? = null,
    val title: @Composable () -> Unit,
    val description: (@Composable () -> Unit)? = null,
    val trailingContent: (@Composable () -> Unit)? = null,
    val enabled: Boolean = true,
    val onClick: (() -> Unit)? = null,
)

@Composable
fun SettingsGroup(title: String? = null, items: List<SettingsItem>)
```

刻意**不抄**的字段：Rhythm 的 `scope`（我们没有 Local/Streaming 之分）、Echo 的 `showBadge`/`isHighlighted`/`scrollToOnHighlight`（那是给设置项搜索定位用的，我们没有搜索）。将来要就再加。

### 6.2 规格建议（取三家交集 + 我们自己的 token）

| 项 | 建议值 | 依据 |
|---|---|---|
| 卡片圆角 | 单项 24；首 24/6，中 6，末 6/24 | Echo 与 Rhythm 一致 |
| 项间距 | 4dp | 三家一致 |
| 卡片底色 | `surfaceContainer` | 跟 Rhythm；Echo 的 `surfaceVariant`@30% 在我们的 AMOLED 档下会偏灰，且我们顶栏刚统一到 `surfaceContainer`（`fc1579e`），一致 |
| elevation | 0 | 三家一致 |
| 行内边距 | h20 / v16 | Echo 20、Rhythm 21，取整 |
| 图标容器 | 40dp，`secondaryContainer` 底，图标 24dp、`onSecondaryContainer` | 跟 Rhythm；与首页底栏选中药丸同一对 token，全 app 语言统一 |
| 图标容器形状 | **待定**，见第七节 | 三家唯一分歧点 |
| 标题 / 副标题 | `titleMedium`/`onSurface` + `bodyMedium`/`onSurfaceVariant`，间隔 2dp | 三家一致 |
| trailing 前间距 | 8dp | 三家一致 |
| 分组标题 | 保持现状（`labelLarge`+`primary`），上边距加到 18dp | Rhythm 的 top18 更透气；样式本来就一致 |

### 6.3 改造范围

| 文件 | 改动 |
|---|---|
| `ui/common/SettingsGroup.kt` | 新增，约 120 行 |
| `ui/settings/SettingsScreen.kt` | 三组共 9 项改为声明式清单；删掉两个 `HorizontalDivider`；预计从 589 行减到 400 行上下 |
| `ui/settings/AboutScreen.kt` | 同款改造（未细读，需先看结构） |
| `ui/settings/AppearanceScreen.kt` | 主体是主题选择卡，**不改**，只有底部零散项按需并入 |
| `ui/profile/ProfileScreen.kt` | 「我的」页也有列表项，可选，建议本轮不动 |

工作量估计：组件 + `SettingsScreen` 半天，含深浅三档验证。`AboutScreen` 另算。

### 6.4 风险点

- **trailing 控件适配**：我们的设置项 trailing 有下拉菜单锚点（`Box` + `DropdownMenu`）、`Switch`、纯文本三种。卡片化后行高变大，下拉菜单的锚点位置要重验，别弹到屏幕外。
- **`LazyColumn` 的 key**：现在每项一个 `item(key=...)`，改成一组一个 `item` 后 key 要重排，注意别让滚动位置跳。
- **iOS 端**：`shared` 里的改动 iOS 同样吃到，但按既有决策不做 iOS 验证（见 `home-redesign-handover.md`）。
- **R8**：纯 UI 改动，无反射，风险低；发版前照常跑 `scripts/release-smoke.sh`。
- **许可证**：Echo 和 Rhythm 都是 GPL-3.0，TrendingAI 是 MIT，**只能照着规格自己写，不能复制代码**；EchoFlow 是 MIT 可复制但需保留版权声明。本文列的是规格数值（尺寸、token、层级），不构成代码复制。

---

## 七、需要拍板的三件事

1. **图标容器形状** — 圆角方 12dp（Echo）／圆形（Rhythm）／多边形 Cookie4Sided（EchoFlow）。倾向圆形：与首页底栏的圆形药丸、头像同语言，最省心；多边形最"Expressive"但要引入 `MaterialShapes` 依赖，且和我们现有的圆角语言差异大。
2. **按压缩放反馈** — 是否抄 Rhythm 的 0.97 缩放（代价是要关掉 ripple 自己接 `interactionSource`）。
3. **改造边界** — 只改 `SettingsScreen`，还是连 `AboutScreen`、「我的」页一起统一。

---

## 附：本文引用的源码位置

| 仓库 | 文件 |
|---|---|
| Echo | `.refs/Echo-Music/app/src/main/kotlin/com/music/echo/ui/component/Material3SettingsGroup.kt`（261 行）、`ui/theme/Theme.kt` |
| Rhythm | `.refs/Rhythm/app/src/main/java/chromahub/rhythm/app/shared/presentation/components/Material3SettingsGroup.kt`（310 行）、`shared/presentation/theme/Theme.kt`、`features/streaming/presentation/screens/GoSettingsScreen.kt:178`（调用范例） |
| EchoFlow | `.refs/EchoFlow/app/src/main/java/com/echoflow/ui/components/ExpressiveComponents.kt:33`（`groupedItemShape`）、`ui/screens/SettingsComponents.kt`（788 行）、`ui/theme/Spacing.kt` |
| TrendingAI | `shared/src/commonMain/kotlin/whl/trending/ai/ui/settings/SettingsScreen.kt`（589 行，`SettingsHeader` 在 :438）、`ui/theme/TrendingTheme.kt` |
