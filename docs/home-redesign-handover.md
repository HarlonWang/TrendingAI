# 首页改版交接文档

对齐 Echo Music 的首页改版，主体已完成并合并。本文交接**剩余工作**与**已定但不写在代码里的决策**。

- 合并 commit：`dcbf7d2 feat(home): 首页改版——tab 重排 + 悬浮胶囊底栏，整体对齐 Echo Music (#81)`
- PR：https://github.com/HarlonWang/TrendingAI/pull/81 （13 个 commit，squash 合入，分支已删）
- 参照物：https://github.com/EchoMusicApp/Echo-Music

---

## 一、最高优先级原则

**照抄 Echo 的实现，不要自作主张改行为。** 想偏离——哪怕自认为更好——必须先与用户确认。

这条是用户在改版中途立的规矩，起因是连续三次自行发挥都判断错了：给 tab 加展开文字标签（Echo 是 `showSelectedLabels = false`，纯图标）、弃用 `HorizontalFloatingToolbar` 自带的 FAB 槽位改成手排 Row（Echo 是槽位 + `widthIn(max = 480.dp)` + 外层居中）、关掉 item 的 ripple 怕和药丸糊（Echo 是先 `clip` 成与药丸同形状再 ripple，不糊）。每次都要用户指出来才回退。

动手前先读 Echo 对应实现，逐项列出「我要怎么做 / Echo 怎么做」，差异集中问一次再开工。

---

## 二、已完成的内容

| 阶段 | 内容 |
|---|---|
| 1 | 个人中心与设置拆成两页；「应用设置」子页取消，四个低频偏好平铺进设置页 |
| 2 | 底栏改为 首页 / Picks / AI 对话 / 我的；三源收进首页用 `SecondaryTabRow` 切换；「我的」升为一级 tab；撤掉顶栏头像入口与 AI 悬浮 FAB |
| 3 | 标准 NavigationBar → M3 Expressive 悬浮胶囊 + 独立「⋯」FAB（设置 / 关于）；滑动药丸选中态；`LocalContentBottomPadding` 解决遮挡 |
| 4（部分） | 切 tab 与页面转场统一 1/8 屏位移 + 200ms 淡化；底栏图标实心/描边双态 |

关键文件：

- `shared/src/commonMain/kotlin/whl/trending/ai/ui/home/HomeScreen.kt` — 首页骨架（623 行）
- `shared/src/commonMain/kotlin/whl/trending/ai/ui/home/HomeFloatingBar.kt` — 悬浮底栏
- `shared/src/commonMain/kotlin/whl/trending/ai/ui/home/HomeTab.kt` — `HomeTab` / `TrendingSource` 枚举
- `shared/src/commonMain/kotlin/whl/trending/ai/ui/home/KidStarIcons.kt` — 自绘 kid_star 图标
- `shared/src/commonMain/kotlin/whl/trending/ai/ui/common/ContentBottomPadding.kt` — 底部留白透传
- `shared/src/commonMain/kotlin/whl/trending/ai/ui/profile/ProfileScreen.kt` — 「我的」tab 内容（无脚手架）
- `shared/src/commonMain/kotlin/whl/trending/ai/ui/settings/SettingsScreen.kt` — 设置页

---

## 三、剩余待办

### A. 阶段 4 未做的两项（用户说「先不做」，随时可捡起）

1. **设置项卡片化** — 对齐 Echo 的 `Material3SettingsGroup` / `Material3SettingsItem`：每行独立圆角卡片（`surfaceContainer` 底）+ 圆角方形 tonal 图标容器 + 小号 primary 色分组标题。当前是裸 `ListItem` + `SettingsHeader`。参照 `app/src/main/kotlin/com/music/echo/ui/component/Material3SettingsGroup.kt`。
2. **页面主标题字号层级** — Echo 用 `displaySmall` / `titleLarge` + Bold，我们还是 `titleMedium`。

### B. 两项从未验证

1. **深色 / AMOLED 观感** — 全程只看过浅色。悬浮胶囊、药丸、双态图标的对比度都没验。
2. **登录态下的「我的」页** — 身份区、额度条、GitHub 入口卡在登录后长什么样，一次都没看过。需真登一次 GitHub。

（iOS 端不作验证要求，见下方「其他产品决策」。）

### C. 文档

**`docs/analytics-notes.md` 补这轮的埋点断点**（目前只写在 commit message 里）：

| 事件 | 变化 |
|---|---|
| `tab_switch` / `tab_double_tap_refresh` | `tab` 取值改为 `home` / `picks` / `chat` / `me`；0.23 是 `trending`/`picks`，更早是 `github`/`hackernews`/`producthunt` |
| `trending_source_switch` | 新增，记录三源子 tab 切换 |
| `profile_open_settings` | 新增，替代 `settings_app_settings`（后者作废，原指向已删除的「应用设置」子页） |
| `settings_about` | 语义不变，但现在有两个触发点（设置页 + 底栏「⋯」菜单） |

拉首页相关漏斗/留存时需按版本切段。

### D. 发版

合并了但**没发版**，线上用户看不到。流程：

1. `scripts/release-smoke.sh` 跑到 PASS（这轮碰了不少 R8 敏感面，不能省）
2. `git tag <版本> && git push origin <版本>`
3. 发版说明走 CI 自动生成，还是本地手写（见 `CLAUDE.md` 的 whatsnew 流程），未定

---

## 四、已定的决策（不要推翻）

### 与 Echo 有意保留的差异

| # | Echo | 我们 | 原因 |
|---|---|---|---|
| 1 | 底栏挂 `Scaffold.bottomBar`，在 NavHost **之外**；进二级页时竖向滑走 | 挂在 `HomeScreen` 内，跟着页面横向滑出 | 用户明确说「先保持现状」。要对齐得把 tab 状态与刷新回调提升到 `App.kt`，属中等重构 |
| 2 | 位移叠加播放器展开进度 | 无播放器 | 不适用 |
| 3 | 底栏内单独判 `pureBlack` | 交给主题层 AMOLED | 已声明 |
| 4 | 有 liquidGlass 档、iOS26 `FloatingTabBar` 档、横屏 `NavigationRail` | 都不做 | 已声明 |
| 5 | FAB 不传 `shape`（androidx 默认即圆） | 显式 `CircleShape` | CMP 版 material3 默认是圆角方形，不传拿不到同款外观 |

### 代码不同但行为相同

- **文字标签整套没实现**。Echo 有完整实现（`AnimatedVisibility` + `expandHorizontally(expandFrom = Start)` + 8dp Spacer + `labelLarge`），只是 `showSelectedLabels = false` 关掉了。将来若要开标签，照 Echo 的做法是**只给选中项加**，并配 12↔16dp 的 padding `animateDp`，不是给四项都挂上。
- **图标来源**：Echo 每 tab 一对自绘 drawable；我们用 material-icons 的 Filled/Outlined 对，其中 kid_star 是自绘（经典 Material Icons 集没有）。
- **底部留白作用域**：Echo 的 `LocalPlayerAwareWindowInsets` 是全局 inset，所有页面消费；我们的 `LocalContentBottomPadding` 只在首页四个内容页有值，二级页拿默认 0（因为二级页不显示底栏）。若将来做「二级页保留底栏」，这里要一起改。

### 其他产品决策

- **子源记忆不外露**：`prefs_trending_source` 每次切子源自动回写，冷启动回到上次那个源，**不做设置项**。它是「记忆」不是「偏好」，与设置页的「默认首页 tab」性质不同。（注：Echo 对应结构是 `rememberSaveable`，根本不持久化，我们这条是经用户确认的超出项）
- **子源切换不加转场**：Echo 的搜索页内部 tab 是 `when (selectedTabIndex)` 硬切，我们照抄。一级 tab 有转场、二级瞬切是对齐后的结果。
- **「关于」有两个入口**（底栏「⋯」菜单 + 设置页通用组末尾），有意为之：菜单是快捷入口，设置是完整清单。别当重复入口删掉。
- **AI 对话是入口不是落点**：点击直接推全屏聊天页，底栏选中态留在原 tab，`HomeTab.Chat` 永远不会成为 `selectedTab` 的取值，也不出现在「默认首页」可选项里。
- **iOS 端不作验证要求**（用户 2026-08-05 决定）：这轮改动只验 Android，不跑 iOS 编译，也不看 iOS 上的三 tab 退化形态。别把它重新加回待办。

---

## 五、坑与注意事项

### 复现 Echo 环境

Echo 仓库在会话 scratchpad 里，会话结束即消失。重新拉：

```bash
git clone --depth 50 https://github.com/EchoMusicApp/Echo-Music.git
```

编译要点：

- **不要用 `gradle.properties.template` 覆盖 `gradle.properties`** —— 后者是入库文件，里面的 `android.newDsl=false` 是必须的；覆盖掉会让 protobuf 插件 0.9.6 在 AGP 9 上炸（`Cannot cast ApplicationExtensionImpl to BaseExtension`）
- 需要 **JDK 21**，本机只有 17；用 Android Studio 自带的：`export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`
- 构建 `:app:assembleArm64FossDebug`（universal 包 107MB，arm64 也有 77MB）
- **模拟器装不下**：Pixel_9_2 的 /data 只剩 400MB 左右，低于系统预留阈值，`INSTALL_FAILED_INSUFFICIENT_STORAGE`。当时是直接看用户已装的 Echo Music 5.2.82 正式版

关键源码位置：

- `app/src/main/kotlin/com/music/echo/ui/component/FloatingNavigationToolbar.kt` — 悬浮底栏（默认档）
- `app/src/main/kotlin/com/music/echo/ui/component/floatingtabbar/FloatingTabBar.kt` — iOS26 档（vendored）
- `app/src/main/kotlin/com/music/echo/ui/screens/search/SearchScreen.kt:371` — 内部 tab（我们三源子 tab 的参照）
- `app/src/main/kotlin/com/music/echo/MainActivity.kt:657` — 底栏显隐白名单；`:1248` — NavHost 转场；`:1066` — 底栏位移换算
- `app/src/main/kotlin/com/music/echo/constants/Dimensions.kt` — 尺寸常量

### 本机环境

- **用户小米 13 上装的是 debug 签名的 release 包**（本地无 `ANDROID_KEYSTORE_PATH`，release 回落 debug 签名）。想装回正式版必须先卸载。应用内更新检查会提示升级但装不上，别拿这台试更新流程。
- 无线调试容易掉线，掉了要用户在手机上重连。
- 模拟器 `emulator` 不在 PATH：`"$ANDROID_HOME/emulator/emulator" -avd Pixel_9_2 -no-snapshot-save -no-boot-anim`

### 已知误报

Sourcery 会报 `.tabIndicatorOffset(selected.ordinal)` 「传 Int 无法编译」。**这是误报**：material3 现在有 `TabRowDefaults.tabIndicatorOffset(selectedTabIndex: Int)` 重载，正是给 `PrimaryTabRow`/`SecondaryTabRow` 的 `indicator` 槽位用的。证据：编译通过、真机渲染正确、Echo 的 `SearchScreen.kt:377` 就是这么写的。

### 其他

- 改/删 `strings.xml` 的 key 后若报 `ActualResourceCollectors` 未解析，删 `shared/build/generated/compose/resourceGenerator` 重新构建
- `HomeScreen.kt` 已 623 行（Sourcery 也提了），把 `barItems` 构建和三个私有顶栏抽出去是合理的后续重构，但不紧急
